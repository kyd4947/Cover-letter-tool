package coverletter.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** 설정 로딩 - 키 우선순위 : ① 환경변수 GEMINI_API_KEY -> ② config.json "geminiApiKey" */
public record AppConfig(String geminiApiKey, List<String> models, double temperature, int maxOutputTokens, Path profilePath, Path outputDir) {

	// 기본 모델을 내보내는 메서드
	public String model() {
		return models.get(0);
	}
	
	private static final Path CONFIG_FILE = Path.of("config.json");			// 프로젝트 루트 (사용자 데이터 dir)
	private static final Gson GSON = new Gson();
	private static final List<String> DEFAULT_MODELS = List.of("gemini-2.5-flash", "gemini-3.8-flash", "gemini-3.5-flash-lite");
	
	public static AppConfig load() {
		JsonObject json = read();
		String key = firstNonBlank(System.getenv("GEMINI_API_KEY"), str(json, "geminiApiKey"));
		if(key == null) {
			throw new IllegalStateException(
					"API 키 없음");
		}
		return new AppConfig(
				key,
				jsonModels(json),
				num(json, "temperature", 0.7),
				(int) num(json, "maxOutputTokens", 4096),
				Path.of(str(json, "profilePath") != null ? str(json, "profilePath") : "profile.md"),
				Path.of(str(json, "outputDir") != null ? str(json, "outputDir") : "output"));
	}
	
	// "models" : [...] 우선 -> 없으면 "model" : "..." -> 없으면 기본 3후보
	private static List<String> jsonModels(JsonObject json){
		List<String> found = new ArrayList<>();
		if(json.has("models") && json.get("models").isJsonArray()) {
			JsonArray arr = json.getAsJsonArray("models");
			for(int i = 0; i < arr.size(); i++) {
				String m = arr.get(i).isJsonPrimitive() ? arr.get(i).getAsString() : null;
				if(m != null && !m.isBlank()) found.add(m.trim());
			}
		}
		if (found.isEmpty()) {
			String single = str(json, "model");
			if(single != null && !single.isBlank()) found.add(single.trim());
		}
		return found.isEmpty() ? DEFAULT_MODELS : List.copyOf(found);
	}
	
	private static String firstNonBlank(String a, String b) {
		if (a != null && !a.isBlank()) return a;
		if (b != null && !b.isBlank()) return b;
		return null;
	}
	
	private static String str(JsonObject json, String name) {
		return json.has(name) && json.get(name).isJsonPrimitive() ? json.get(name).getAsString() : null;
	}
	
	private static double num(JsonObject json, String name, double dflt) {
		return json.has(name) && json.get(name).isJsonPrimitive() ? json.get(name).getAsDouble() : dflt;
	}
	
	private static JsonObject read() {
		if(!Files.exists(CONFIG_FILE)) return new JsonObject();
		try {
			JsonObject parsed = GSON.fromJson(Files.readString(CONFIG_FILE), JsonObject.class);
			return parsed != null ? parsed : new JsonObject();
		} catch(IOException | RuntimeException e) {
			return new JsonObject();			// 깨지거나 빠진 config.json 은 기본값으로 통과
		}
	}
}
