package coverletter.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// txt · md - 이미 텍스트라 그대로 읽기
public class TextExtractor implements Extractor {
	
	@Override
	public String extract(Path file) throws IOException{
		return Files.readString(file).strip();
	}

}
