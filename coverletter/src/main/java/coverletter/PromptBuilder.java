package coverletter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import coverletter.profile.ProfileStore;

/**
 * 프롬프트 조립
 * 구성 3계층 : guides 항목별 작성법(외부 파일) + 이 조립 로직(고정 코드) + 런타임 데이터(공고/경력/역량/이전초안)
 * system 원칙 : 사실 기반, 창작 금지, 글자 수 준수(±10% 유도), 존댓말 문체
 */
public class PromptBuilder {

	// 정제된 공고 - 원문 텍스트 + 제목 (회사 / 직무명 기준)
	public record Posting(String sourceText, String finalTitle) {}
	
	private final SectionGuideStore guides = new SectionGuideStore();
	
	// LLM 1차 - 공고에서 요구역량 3-4개 도출
	public Prompt buildStrenghPrompt(Posting posting) {
		String system = """
				너는 한국 채용 공고 분석기다. 공고 본문에서 지원자에게 요구되는 역량 3~4개를 추린다.
				각 역량은 한 항목씩, "- " 형태로 시작하는 한 줄, 20자 이내로 압축한다.
				목록 이외의 문장은 출력하지 않는다.		
				""";
		String user = "채용공고 원문:\n" + posting.sourceText();
		return new Prompt(system, user);
	}
	
	// LLM 2차 - 단일 항목 초안. priorDraft는 다듬기 재호출 시 이전 초안, 첫 생성은 null
	public Prompt buildSectionPrompt(Posting posting, ProfileStore.Profile profile,
			List<String> strengths, Section section, String priorDraft) throws IOException {
		SectionGuideStore.SectionGuide guide = guides.get(section.title());
		
		String system = """
				너는 한국 채용 자기소개서 작성 보조 에이전트다.
				사실 기반으로만 쓴다. 제공된 경력 자료에 없는 사실을 창작하지 않는다.
				본문 글자수는 공백 포함, 제한 글자수의 85% 이상 100% 이하로 맞춘다.
				문체는 존댓말 정중형으로, 개조식(목록형)이 아니라 자연스러운 문단으로 쓴다.
				소제목, "제 답변은" 같은 머리말과 말끝퍼즐 없이, 본문 텍스트만 출력한다.
				""";
		
		// user 조립 순서 : ① 공고 ② 역량 ③ profile + 가이드 ④ 항목 지시 ⑤ 이전 초안
		StringBuilder user = new StringBuilder();
		user.append("[채용공고 원문]\n").append(posting.sourceText()).append("\n\n");
		user.append("[필요 역량 - 공고 분석 결과]\n");
		for(String strength : strengths) {
			user.append("- ").append(strength).append('\n');
		}
		user.append('\n');
		user.append("[내 경력 자료 - 여기에만 있는 사실을 근거로 사용]\n")
			.append(profile.rawMarkdown())
			.append("\n\n");
		user.append("[이 항목 작성 가이드]\n").append(guide.writingGuide()).append("\n\n");
		user.append("[이번 항목]\n")
			.append(section.title())
			.append(" - 공백 포함 ")
			.append(section.charLimit())
			.append("자\n");
		if (priorDraft != null && !priorDraft.isBlank()) {
			user.append("\n[이전 초안 - 이 문단을 다듬을 것]\n").append(priorDraft).append('\n');
		}
		
		return new Prompt(system, user.toString());
	}
	
	// 양식 감지 - 공고에서 자조서 항목 + 글자 수 유추. 실패 시 호출 측이 기본 양식 사용 (fail-closed)
	public Prompt buildFormPrompt(Posting posting) {
		String system = """
				너는 한국 채용 자기소개서 양식 분석기다. 공고 본문에서 자기소개서 작성 항목과 글자수 제한을 유추한다.
				글자수가 공고에 명시가 없으면 유형별 통상치를 쓴다 (성장과정·지원동기 500자, 입사 후 포부 400자 등).
				항목은 2~5개. 출력은 아래 형식만, 항목당 한 줄:
				항목: (항목명) / 글자수: (제한)
				""";
		String user = "채용공고 원문:\n" + posting.sourceText();
		return new Prompt(system, user);
	}
	
	// 감지 응답 파싱 - "항목: x / 글자수 : n" 줄만 수용. 이상하면 빈 목록 (기본 양식 폴백 트리거)
	public static List<Section> parseForm(String llmOutput) {
		List<Section> form = new ArrayList<>();
		for(String line : llmOutput.lines().toList()) {
			String s = line.strip();
			if(!s.startsWith("항목:")) {
				continue;
			}
			int at = s.indexOf("글자 수:");
			String title = at > 3 ? s.substring("항목:".length(), at).replace("/", "").strip() : "";
			String num = at >= 0 ? s.substring(at + "글자 수:".length()).replaceAll("[^0-9]", "") : "";
			if(!title.isEmpty() && !num.isEmpty()) {
				int limit = Integer.parseInt(num);
				if(limit >= 200 && limit <= 2000) {			// 환각 필터 - 상식 범위 밖 글자수 폐기
					form.add(new Section(title, limit));
				}
			}
			if(form.size() >= 5) {
				break;
			}
		}
		return form;
	}
	
	// 1차 응답 문자열 -> 역량 목록
	public static List<String> parseStrengths(String llmOutput){
		return llmOutput.lines()
				.map(line -> line.strip().replaceFirst("^[-•*\\d.)]+\\s*", ""))
				.filter(line -> !line.isEmpty())
				.limit(5)
				.toList();
	}
}