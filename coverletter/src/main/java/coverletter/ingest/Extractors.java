package coverletter.ingest;

import java.nio.file.Path;
import java.util.Locale;

// 확장자 -> 추출기 | 미지원은 오류
public class Extractors {
	
	public static Extractor of(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
		
		return switch(ext) {
			case "docx" -> new DocxExtractor();
			case "txt" -> new TextExtractor();
			default -> throw new Extractor.ExtractException("지원형식 아님 : ." + ext + "(확장 계획) : " + file, null);
		};
	}
}
