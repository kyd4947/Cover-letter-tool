package coverletter.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Scanner;

import coverletter.Prompt;
import coverletter.config.AppConfig;
import coverletter.llm.GeminiClient;

// 적재 파이프라인 : 원본 -> 추출 -> 요약 카드 -> 승인(y/n) -> profile 파일 반영
public class Ingestor {

	// 반영 대상
	private static final Path PROFILE = Path.of("profile.md");
	
	private final GeminiClient gemini = new GeminiClient();
	
	// 요약 카드 양식 - "제목...." 하나는 반영 시 섹션 제목으로 씀
	private static final String CARD_SYSTEM = """
			너는 개인 경력 자료 분석기다. 제공된 자료에서 자기소개서 재료로 쓸 사실만 정리한다.
			자료에 없는 사실을 만들지 않는다. 근거 없는 칭찬 표현 금지.
			출력은 아래 양식만, 각 항목은 한 줄:
			제목: (프로젝트 혹은 활동명, 20자 이내)
			목적: (만들거나 한 이유)
			기술: (실제 사용한 것)
			과제: (직면한 문제)
			결과: (자료에 실제 근거가 있는 것만)
			""";
	
	public void ingest(AppConfig config, List<Path> files) throws IOException{
		Scanner input = new Scanner(System.in);
		for(Path file : files) {
			try {
				String text = Extractors.of(file).extract(file);
				if (text.isBlank()) {
					System.out.println("[스킵] " + file.getFileName() + " - 추출한 텍스트가 비어 있음");
					continue;
				}
				
				// 1) 요약 카드
				String user = "자료 원문:\n" + text.strip();
				String card = gemini.generate(config, new Prompt(CARD_SYSTEM, user)).strip();
				
				System.out.println();
				System.out.println("### " + file.getFileName() + " 요약 카드 ###");
				System.out.println(card);
				System.out.print("\n profile.md에 반영할까요? (y/n) : ");
				if (!isYes(input.nextLine())) {
					System.out.println("[유지하다] 반영하지 않음 - 다음 파일로");
					continue;
				}
				
				// 2) 승인 -> 중복 검사 후 반영
				String title = cutTitle(card, file);
				if(profileHas(title)) {
					System.out.println("[스킵] '" + title + "' 섹션이 profile.md에 이미 있음");
					continue;
				}
				appendSection(title, card);
				System.out.println("반영 완료 - profile.md에 '" + title + "' 섹션 추가");
			} catch (Extractor.ExtractException e) {
				System.out.println("[스킵] " + file.getFileName() + " - " + e.getMessage());
			}
		}
	}
	
	// 키보드 타이핑 : y 이면 승인. 그 외 전부 보류
	private static boolean isYes(String typed) {
		return typed != null && typed.strip().equalsIgnoreCase("y");
	}
	
	// 카드의 "제목:...." 줄에서 제목을 뽑음. 없으면 파일명 (확장자 제거)
	private static String cutTitle(String card, Path file) {
		for(String line : card.lines().toList()) {
			if(line.strip().startsWith("제목:")) {
				return line.strip().substring("제목:".length()).strip();
			}
		}
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? name : name.substring(0, dot);
	}
	
	// profile.md를 줄 단위로 훑으면 "## 제목" 섹션 중복 확인 (gitignore 대상, 파일 유무 무관)
	private boolean profileHas(String title) throws IOException{
		if(!Files.exists(PROFILE)) {
			return false;
		}
		String heading = "## " + title.strip();
		for(String line : Files.readAllLines(PROFILE)) {
			if(line.strip().equals(heading)) {
				return true;
			}
		}
		return false;
	}
	
	// profile.md 맨 뒤에 "\n\n## 제목 \n\n (카드 본문)" 추가 - 제목 줄은 제거
	private void appendSection(String title, String card) throws IOException{
		StringBuilder section = new StringBuilder();
		section.append("\n\n## ").append(title.strip()).append("\n\n");
		for(String line : card.lines().toList()) {
			if(!line.strip().startsWith("제목:")) {
				section.append(line).append('\n');
			}
		}
		Files.writeString(PROFILE, section.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}
}
