package coverletter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import coverletter.config.AppConfig;
import coverletter.crawl.PostingScraper;
import coverletter.llm.GeminiClient;
import coverletter.output.CompanyInfo;
import coverletter.output.OutputSaver;
import coverletter.output.OutputSaver.SectionDraft;
import coverletter.profile.ProfileStore;

public class Main {
	
	// 기본 양식 - 수동 선택 · 감지 실패 시 폴백
	public static final List<Section> DEFAULT_FORM = List.of(
			new Section("성장과정", 500),
			new Section("지원동기", 500),
			new Section("성격 장단점", 500),
			new Section("입사 후 포부", 400));
	
	// 다듬기 재호출용 컨텍스트 - 재크롤/재분석 없이 이전 결과를 이어 쓴다
	public record GenerationContext(PromptBuilder.Posting posting, ProfileStore.Profile profile, List<String> strengths) {}
	
	// 생성 완료 결과 - 저장 위치 + 초안 + 회사 정보 + 다듬기 컨텍스트
	public record GenerationResult(Path saved, List<SectionDraft> drafts, CompanyInfo company, GenerationContext context) {}
	
	public static void main(String[] args) {
		try {
			if(args.length < 2) {
				System.err.println("사용법 : Main <공고URL|공고텍스트파일> <회사명>");
				return;
			}
			generate(args[0], args[1], System.out::println);
		} catch (GeminiClient.GeminiException e) {
			System.err.println("[호출 실패] " + e.getMessage());
		} catch (IllegalStateException e) {
			System.err.println("[설정 문제] " + e.getMessage());
		} catch (Exception e) {
			System.err.println("[오류] " + e.getMessage());
		}
	}
	
	// GUI · CLI 공용 생성 흐름 - 양식 자동 감지 (기존 호출측 무손상)
	public static GenerationResult generate(String arg0, String company, Consumer<String> log) throws Exception {
		return generate(arg0, company, null, log);
	}

	// GUI · CLI 공용 생성 흐름 - manualForm 이 있으면 LLM 감지 생략
	// 중단 케이스 (빈 본문 · 역량 실패)는 null 반환
	public static GenerationResult generate(String arg0, String company, List<Section> manualForm, Consumer<String> log) throws Exception {
		// 0) 설정 + 키
		AppConfig config = AppConfig.load();
		log.accept("키 로딩 OK - 모델 : " + config.model());
		
		// 가이드 사용자 폴더 - 첫 실행 시 번들 복사 (실패해도 번들 가이드로 계속)
		try {
			SectionGuideStore.ensureUserGuides();
		} catch (IOException e) {
			log.accept("[경고] guides 사용자 폴더 준비 실패 - 번들 가이드 사용 (" + e.getMessage() + ")");
		}
		
		GeminiClient gemini = new GeminiClient();
		
		// 1) 공고 확보 - http로 시작하면 크롤, 아니면 텍스트 파일 경로 (백업 모드)
		PostingScraper scraper = new PostingScraper();
		String postingText;
		if(arg0.startsWith("http")) {
			postingText = scraper.fetch(arg0);
			if(scraper.looksEmpty(postingText)) {
				log.accept("본문이 비어 있음(JS 렌더링 페이지로 추정)");
				log.accept("대응 : 공고 본문을 posting.txt 에 붙여넣고 -> Main posting.txt " + company);
				return null;
			}
		} else {
			postingText = Files.readString(Path.of(arg0));
		}
		
		// 2) 내 경력 자료
		ProfileStore.Profile profile = new ProfileStore().load(config.profilePath());
		log.accept("profile 로딩 OK - 프로젝트 " + profile.projects().size() + " 개");
		
		PromptBuilder builder = new PromptBuilder();
		PromptBuilder.Posting posting = new PromptBuilder.Posting(postingText, company);
		
		// 3) LLM 1차 - 요구역량 추출
		String raw = gemini.generate(config, builder.buildStrenghPrompt(posting));
		List<String> strengths = PromptBuilder.parseStrengths(raw);
		if(strengths.isEmpty()) {
			log.accept("[오류] 역량 추출 실패 - 확인용 응답: \n" + raw);
			return null;
		}
		log.accept("요구역량 " + strengths.size() + "개 -> " + strengths);
		GenerationContext context = new GenerationContext(posting, profile, strengths);
		
		// 4) 양식 - 수동 지정이 있으면 그대로, 없으면 LLM 감지 (실패 시 기본 양식)
		List<Section> form;
		if(manualForm != null && !manualForm.isEmpty()) {
			form = manualForm;
			log.accept("지정 양식 사용 - " + form.size() + "항목 -> " + form);
		} else {
			try {
				form = PromptBuilder.parseForm(gemini.generate(config, builder.buildFormPrompt(posting)));
			} catch (GeminiClient.GeminiException e) {
				log.accept("양식 감지 호출 실패 - 기본 양식 사용 (" + e.getMessage() + ")");
				form = List.of();
			}
			if (form.isEmpty()) {
				log.accept("양식 감지 실패 - 기본 양식 사용 (성장 과정 / 지원 동기 / 성격 장단점 / 입사 후 포부)");
				form = DEFAULT_FORM;
			} else {
				log.accept("양식 감지 OK - " + form.size() + "항목 -> " + form);
			}
		}
		
		// 5) LLM 2차 - 항목별 초안
		List<SectionDraft> drafts = new ArrayList<>();
		for(Section section : form) {
			log.accept("생성 중 : " + section.title() + " .... ");
			String text = gemini.generate(config, builder.buildSectionPrompt(posting, profile, strengths, section, null));
			int count = (int) text.strip().codePoints().count();
			
			// 글자 수 clamp - 초과 시 압축 재호출 1회
			if(count > section.charLimit()) {
				log.accept("                " + count + "자 초과 - 압축 재호출 .... ");
				String trimmed = gemini.generate(config, builder.buildTrimPrompt(section, text.strip())).strip();
				int trimmedCount = (int)trimmed.codePoints().count();
				if(trimmedCount <= section.charLimit()) {
					text = trimmed;
					count = trimmedCount;
					log.accept("            압축 성공 - " + count + "자");
				} else if(trimmedCount < count) {
					text = trimmed;
					count = trimmedCount;
					log.accept("         [경고] 재호출 후에도 " + count + "자 초과 - 더 가까운 것 채택");
				} else {
					log.accept("         [경고] 재호출 무효 - 원본 " + count + "자 유지");
				}
			}
			log.accept("          완료 - " + count + "자 (제한 " + section.charLimit() + "자)");
			drafts.add(new SectionDraft(section, text.strip(), count));
		}
		
		// 6) 저장
		CompanyInfo info = new CompanyInfo(company, LocalDate.now());
		Path saved = new OutputSaver(config.outputDir()).save(info, drafts);
		log.accept("저장 성공 : " + saved.toAbsolutePath());
		return new GenerationResult(saved, List.copyOf(drafts), info, context);
	}
	
	// 다듬기 - 이전 초안 + 수정 지시로 해당 항목만 재호출, 저장 파일도 같은 경로에 갱신
	public static GenerationResult refine(GenerationResult result, int index, String instruction, Consumer<String> log) throws Exception {
		if(index < 0 || index >= result.drafts().size()) {
			throw new IllegalArgumentException("항목 번호 범위 밖 : " + index);
		}
		AppConfig config = AppConfig.load();
		GeminiClient gemini = new GeminiClient();
		PromptBuilder builder = new PromptBuilder();
		SectionDraft old = result.drafts().get(index);
		Section section = old.section();
		
		log.accept("다듬기 : " + section.title() + " .... ");
		String text = gemini.generate(config, builder.buildSectionPrompt(
				result.context().posting(), result.context().profile(), result.context().strengths(),
				section, old.text(), instruction)).strip();
		if(text.isEmpty()) {
			throw new IllegalStateException("다듬기 응답이 비어 있음");
		}
		int count = (int) text.codePoints().count();
		
		// 글자 수 clamp - 생성과 같은 규칙 (초과 시 압축 1회, 더 가까운 것 채택)
		if(count > section.charLimit()) {
			log.accept("                " + count + "자 초과 - 압축 재호출 .... ");
			String trimmed = gemini.generate(config, builder.buildTrimPrompt(section, text)).strip();
			int trimmedCount = (int) trimmed.codePoints().count();
			if(trimmedCount <= section.charLimit() || trimmedCount < count) {
				text = trimmed;
				count = trimmedCount;
			}
			log.accept("          압축 후 " + count + "자 (제한 " + section.charLimit() + "자)");
		}
		
		List<SectionDraft> drafts = new ArrayList<>(result.drafts());
		drafts.set(index, new SectionDraft(section, text, count));
		new OutputSaver(config.outputDir()).rewrite(result.saved(), result.company(), drafts);
		log.accept("다듬기 완료 - " + count + "자 / 파일 갱신 : " + result.saved().getFileName());
		return new GenerationResult(result.saved(), List.copyOf(drafts), result.company(), result.context());
	}
}
