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
			case "txt", "md" -> new TextExtractor();
			case "pptx" -> new PptxExtractor();
			case "hwpx" -> new HwpxExtractor();
			default -> throw new Extractor.ExtractException("지원형식 아님 : ." + ext + "(확장 계획) : " + file, null);
		};
	}
	
	// 폴더 배치용 - 이 파일이 적재 대상 확장자인지 확인
	public static boolean supported(Path file) {
		try {
			of(file);
			return true;
		}catch(Extractor.ExtractException e) {
			return false;
		}
	}
}
