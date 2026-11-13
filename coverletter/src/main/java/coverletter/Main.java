package coverletter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import coverletter.config.AppConfig;
import coverletter.crawl.PostingScraper;
import coverletter.llm.GeminiClient;
import coverletter.output.CompanyInfo;
import coverletter.output.OutputSaver;
import coverletter.output.OutputSaver.SectionDraft;
import coverletter.profile.ProfileStore;

public class Main {
	
	// 기본 양식 - 항목 감지/수동 입력으로 확장
	private static final List<Section> DEFAULT_FORM = List.of(
			new Section("성장과정", 500),
			new Section("지원동기", 500),
			new Section("성격 장단점", 500),
			new Section("입사 후 포부", 400));
	
	public static void main(String[] args) {
		try {
			if(args.length < 2) {
				System.err.println("사용법 : Main <공고URL|공고텍스트파일> <회사명>");
				return;
			}
			String arg0 = args[0];
			String company = args[1];
			
			// 1) 설정 + 키
			AppConfig config = AppConfig.load();
			System.out.println("키 로딩 OK - 모델 : " + config.model());
			GeminiClient gemini = new GeminiClient();
			
			// 2) 공고 확보 - http로 시작하면 크롤, 아니면 텍스트 파일 경로 (백업 모드)
			PostingScraper scraper = new PostingScraper();
			String postingText;
			if(arg0.startsWith("http")) {
				postingText = scraper.fetch(arg0);
				if(scraper.looksEmpty(postingText)) {
					System.err.println("본문이 비어 있음 (JS 렌더링 페이지로 추정)");
					System.err.println("대응 : 공고 본문을 posting.txt 에 붙여넣고 -> Main posting.txt " + company);
					return;
				}
			} else {
				postingText = Files.readString(Path.of(arg0));
			}
			
			// 3) 내 경력 자료
			ProfileStore.Profile profile = new ProfileStore().load(config.profilePath());
			System.out.println("profile 로딩 OK - 프로젝트 " + profile.projects().size() + "개");
			
			PromptBuilder builder = new PromptBuilder();
			PromptBuilder.Posting posting = new PromptBuilder.Posting(postingText, company);
			
			// 4) LLM 1차 - 요구역량 추출
			String raw = gemini.generate(config, builder.buildStrenghPrompt(posting));
			List<String> strengths = PromptBuilder.parseStrengths(raw);
			if(strengths.isEmpty()) {
				System.err.println("역량 추출 실패 - 확인용 응답:\n" + raw);
				return;
			}
			System.out.println("요구역량 " + strengths.size() + "개 -> " + strengths);
			
			// 5) LLM 양식 감지 - 항목 + 글자 수 유추, 실패 시 기본 양식 (fail - closed)
			List<Section> form;
			try {
				form = PromptBuilder.parseForm(gemini.generate(config, builder.buildFormPrompt(posting)));
			} catch (GeminiClient.GeminiException e) {
				System.out.println("양식 감지 호출 실패 - 기본 양식 사용 (" + e.getMessage() + ")");
				form = List.of();
			}
			if (form.isEmpty()) {
				System.out.println("양식 감지 실패 - 기본 양식 사용 (성장 과정 / 지원 동기 / 성격 장단점 / 입사 후 포부)");
				form = DEFAULT_FORM;
			} else {
				System.out.println("양식 감지 OK - " + form.size() + "항목 -> " + form);
			}
			
			// 6) LLM 2차 - 항목별 초안 (순차 5호출)
			List<SectionDraft> drafts = new ArrayList<>();
			for(Section section : form) {
				System.out.println("생성 중 : " + section.title() + " ...");
				String text = gemini.generate(config, builder.buildSectionPrompt(posting, profile, strengths, section, null));
				int count = (int) text.strip().codePoints().count();
				
				// 글자 수 clamp - 초과 시 압축 재호출 1회
				if(count > section.charLimit()) {
					System.out.println("           " + count + "자 초과 - 압축 재호출 ...");
					String trimmed = gemini.generate(config, builder.buildTrimPrompt(section, text.strip())).strip();
					int trimmedCount = (int) trimmed.codePoints().count();
					if(trimmedCount <= section.charLimit()) {
						text = trimmed;
						count = trimmedCount;
						System.out.println("              압축 성공 - " + count + "자");
					} else if (trimmedCount < count) {
						text = trimmed;
						count = trimmedCount;
						System.out.println("          [경고] 재호출 후에도 " + count + "자 초과 - 더 가까운 것 채택");
					} else {
						System.out.println("          [경고] 재호출 무효 - 원본 " + count + "자 유지");
					}
				}
				System.out.println("       완료 - " + count + "자 (제한 " + section.charLimit() + "자)");
				drafts.add(new SectionDraft(section, text.strip(), count));
			}
			
			// 7) 저장
			Path saved = new OutputSaver(config.outputDir()).save(new CompanyInfo(company, LocalDate.now()), drafts);
			System.out.println("저장 성공 : " + saved.toAbsolutePath());
		} catch (GeminiClient.GeminiException e) {
			System.err.println("[호출 실패] " + e.getMessage());
		} catch (IllegalStateException e) {
			System.err.println("[설정 문제] " + e.getMessage());
		} catch (Exception e) {
			System.err.println("[오류] " + e.getMessage());
		}
	}
}
