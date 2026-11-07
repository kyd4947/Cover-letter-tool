package coverletter.ingest;

import java.io.IOException;
import java.nio.file.Path;

// 원본 자료 1개 -> 순수 텍스트 1개 (적재의 첫 단계)
public interface Extractor {

	// 변환 실패 (형식이 맞지 않음 · 내용 비어 있음 등)는 여기로. 호출자가 스킵할 수 있도록 RuntimeException
	class ExtractException extends RuntimeException{
		public ExtractException(String message, Throwable cause) {
			super(message, cause);
		}
	}
	
	String extract(Path file) throws IOException;
}
