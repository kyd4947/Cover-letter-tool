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
	
	private static final Path OUTPUT_DIR = Path.of("output");  // config.outputDir 사용
	
	public Path save(CompanyInfo company, List<SectionDraft> drafts) throws IOException {
		Files.createDirectories(OUTPUT_DIR);
		
		String stem = sanitize(company.companyName().strip()) + "_" + company.appliedDate();
		Path target = OUTPUT_DIR.resolve(stem + ".md");
		int suffix = 2;
		while (Files.exists(target)) {
			target = OUTPUT_DIR.resolve(stem + "_" + suffix + ".md");
			suffix += 1;
		}
		
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
		return Files.writeString(target, md.toString());
	}
	
	// 파일명 불가 문자 치환 (회사명에 /:*? 등이 섞여도 이상")
	private static String sanitize(String name) {
		return name.replaceAll("[\\\\/:*?\"<>|]", "-").strip();
	}
}
