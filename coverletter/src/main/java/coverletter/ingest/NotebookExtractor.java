package coverletter.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// .ipynb 적재 - 코드 셀 + 마크다운 셀만 추출 (결과값 · 이미지 출력 제외)
public class NotebookExtractor implements Extractor {
	private static final Gson GSON = new Gson();
	
	@Override
	public String extract(Path file) throws IOException{
		JsonObject root;
		try {
			root = GSON.fromJson(Files.readString(file), JsonObject.class);
		} catch (Exception e) {
			throw new Extractor.ExtractException("JSON 파싱 실패 : " + file, e);
		}
		if(root == null || !root.has("cells")) {
			throw new Extractor.ExtractException("ipynb 구조 아님 (cells 없음) : " + file, null);
		}
		StringBuilder sb = new StringBuilder();
		for(JsonElement cell : root.getAsJsonArray("cells")) {
			JsonObject c = cell.getAsJsonObject();
			String type = c.has("cell_type") ? c.get("cell_type").getAsString() : "";
			if(!type.equals("code") && !type.equals("markdown")) {
				continue;						// raw 등 - 대상 아님
			}
			String text = sourceText(c);
			if(text.isBlank()) {
				continue;
			}
			sb.append(text.strip()).append('\n');
			if(type.equals("code")) {
				sb.append('\n');
			}
		}
		return sb.toString().strip();
	}
	
	// source는 문자열이거나 문자열 배열이므로 (ipynb 표준) 둘 다 수용
	private static String sourceText(JsonObject cell) {
		if(!cell.has("source")) {
			return "";
		}
		JsonElement source = cell.get("source");
		if(source.isJsonArray()) {
			StringBuilder sb = new StringBuilder();
			for(JsonElement line : source.getAsJsonArray()) {
				sb.append(line.getAsString());
			}
			return sb.toString();
		}
		return source.getAsString();
	}
}
