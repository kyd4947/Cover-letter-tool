package coverletter.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
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
			다른 프로젝트·활동이 여럿 보이면 각각 하나씩 카드를 출력하고 카드 사이는 구분줄 === 로 나눈다.
			출력은 아래 양식만, 각 항목은 한 줄:
			제목: (프로젝트 혹은 활동명, 20자 이내)
			목적: (만들거나 한 이유)
			기술: (실제 사용한 것)
			과제: (직면한 문제)
			결과: (자료에 실제 근거가 있는 것만)
			""";
	
	// 이미지 pdf 전사용 - 원문만, 요약 금지 (카드 관문이 요약 역할을 이미 갖고 있음)
	private static final String OCR_SYSTEM = """
			너는 pdf 원문 전사기다. 이미지 문서든 텍스트 문서든 문서 안의 모든 글자를
			문서 순서대로 평문으로 출력한다. 요약·해설·추가 문장 없이 원문 그대로만.
			""";
	
	public void ingest(AppConfig config, List<Path> files) throws IOException{
		Scanner input = new Scanner(System.in);
		for(Path file : files) {
			try {
				String text = Extractors.of(file).extract(file);
				if (text.isBlank() && isPdf(file)) {
					System.out.println("pdf 텍스트 없음 - 이미지 pdf 추정 : Gemini vision 폴백 ...");
					text = pdfTextByVision(config, file);
				}
				if (text.isBlank()) {
					System.out.println("[스킵] " + file.getFileName() + " - 추출한 텍스트가 비어 있음");
					continue;
				}
				
				// 1) 요약 카드
				System.out.println("적재 중 : " + file.getFileName() + "...");			// 침묵 구간 진행줄
				String user = "자료 원문:\n" + text.strip();
				String card = gemini.generate(config, new Prompt(CARD_SYSTEM, user)).strip();

				// 2) 다중 카드 분할 -> 카드별 승인
				List<String> cards = splitCards(card);
				if(cards.isEmpty()) {
					System.out.println("[스킵] " + file.getFileName() + " - 카드 재료 없음");
					continue;
				}
				for (String one : cards) {
					System.out.println();
					System.out.println("### " + file.getFileName() + " 요약 카드 ###");
					System.out.println(one);
					// 제목을 승인 전에 뽑아 근사 중복 의심 표시 -> 사람이 제목 보고 판단
					String title = cutTitle(one, file);
					String similar = similarTitle(title);
					if(similar != null) {
						System.out.println("[중복 의심] '" + title + "' 는 기존 '" + similar + "' 와 제목이 유사 - 중복 아니면 y, 같은 항목이면 n");
					}
					if(profileHas(title)) {
						System.out.println("[스킵] '" + title + "' 섹션이 profile.md에 이미 있음");
						continue;
					}
					System.out.print("\n profile.md에 반영할까요? (y/n) : ");
					if (!isYes(input.nextLine())) {
						System.out.println("[유지] 반영하지 않음 - 다음 파일로");
						continue;
					}
					appendSection(title, one);
					System.out.println("반영 완료 - profile.md에 '" + title + "' 섹션 추가");
				}
			} catch (Extractor.ExtractException e) {
				System.out.println("[스킵] " + file.getFileName() + " - " + e.getMessage());
			}
		}
	}
	// 카드 사이 구분줄(= 로만 된 줄) 기준 분할 - 구분줄 없으면 카드 1장
	private static List<String> splitCards(String card){
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for(String line : card.lines().toList()) {
			if(line.strip().matches("=+")) {
				if(!current.isEmpty()) {
					parts.add(current.toString().strip());
					current.setLength(0);
				}
			} else {
				current.append(line).append('\n');
			}
		}
		if(!current.isEmpty()) {
			parts.add(current.toString().strip());
		}
		return parts;
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
	
	// 근사 중복 의심 표시
	private String similarTitle(String title) throws IOException{
		if(!Files.exists(PROFILE)) {
			return null;
		}
		String key = normalize(title);
		if(key.isEmpty()) {
			return null;
		}
		for(String line : Files.readAllLines(PROFILE)) {
			if(line.startsWith("## ")) {
				String existing = line.substring(3).strip();
				if(existing.equals(title.strip())) {
					continue;				// 완전 일치는 이 곳이 아닌 profileHas 관문
				}
				String other = normalize(existing);
				int shorter = Math.min(key.length(), other.length());
				if(shorter > 0 && commonPieces(key,other) >= shorter * 0.6) {
					return existing;
				}
			}
		}
		return null;
	}
	
	// 제목 정규화 : 공백, 기호 제거 + 영문 소문자
	private static String normalize(String title) {
		return title.replaceAll("[^0-9가-힣a-zA-Z]", "").toLowerCase();
	}
	
	// 두 문자열에 겹치는 가장 긴 연속 조각의 길이(제목은 20자 수준)
	private static int commonPieces(String a, String b) {
		int best = 0;
		for(int start = 0; start < a.length(); start++) {
			for(int end = start + 1; end <= a.length(); end++) {
				String piece = a.substring(start, end);
				if(b.contains(piece) && piece.length() > best) {
					best = piece.length();
				}
			}
		}
		return best;
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
	
	// vision 폴백 대상 확장자 (현재 pdf만)
	private static boolean isPdf(Path file) {
		return file.getFileName().toString().toLowerCase().endsWith(".pdf");
	}
	
	// pdf 원문을 통째로 inline_data에 실어 Gemini가 전사 (18MB 직전 풀 실패 → fail-closed)
	private String pdfTextByVision(AppConfig config, Path file) throws IOException {
		byte[] media = Files.readAllBytes(file);
		if (media.length > 18 * 1024 * 1024) {
			System.out.println("[스킵] " + file.getFileName() + " - pdf 용량 초과 (폴백 미사용)");
			return "";
		}
		return gemini.generate(config, new Prompt(OCR_SYSTEM, "아래 pdf 원문을 전사한다."), media, "application/pdf");
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
