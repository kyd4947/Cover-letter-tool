package coverletter.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import coverletter.Section;

// 결과 저장. 기본 경로: output/{회사명}_{YYYY-MM-DD}.md
public class OutputSaver {

	// 항목 1개의 완성본 - 실제 글자수는 LLM 계산을 믿지 않고 codePoints 실측 값을 넣음
	public record SectionDraft(Section section, String text, int charCount) {}
	
	private final Path outputDir;
	
	// 기본값 유지 생성자 - 기존 호출측 무손상
	public OutputSaver() {
		this(Path.of("output"));
	}
	
	// config.outputDir 주입 생성자
	public OutputSaver(Path outputDir) {
		this.outputDir = outputDir;
	}
	
	public Path save(CompanyInfo company, List<SectionDraft> drafts) throws IOException {
		Files.createDirectories(outputDir);
		
		String stem = sanitize(company.companyName().strip()) + "_" + company.appliedDate();
		Path target = outputDir.resolve(stem + ".md");
		int suffix = 2;
		while (Files.exists(target)) {
			target = outputDir.resolve(stem + "_" + suffix + ".md");
			suffix += 1;
		}
		
		return Files.writeString(target, toMarkdown(company, drafts));
	}
	
	// 다듬기 후 같은 파일 갱신 - 접미사 없이 덮어씀
	public Path rewrite(Path target, CompanyInfo company, List<SectionDraft> drafts) throws IOException {
		return Files.writeString(target, toMarkdown(company, drafts));
	}
	
	// 저장 본문 조립 - save / rewrite 공용
	private static String toMarkdown(CompanyInfo company, List<SectionDraft> drafts) {
		StringBuilder md = new StringBuilder();
		md.append("# ").append(company.companyName())
			.append(" 자기소개서 (").append(company.appliedDate()).append(")\n\n");
		for(SectionDraft draft : drafts) {
			md.append("## ").append(draft.section().title())
				.append(" (").append(draft.section().charLimit())
				.append("자 / 실제 ").append(draft.charCount()).append("자)\n\n")
				.append(draft.text())
				.append("\n\n");
		}
		return md.toString();
	}
	
	// 파일명 불가 문자 치환 (회사명에 /:*? 등이 섞여도 이상")
	private static String sanitize(String name) {
		return name.replaceAll("[\\\\/:*?\"<>|]", "-").strip();
	}
}
