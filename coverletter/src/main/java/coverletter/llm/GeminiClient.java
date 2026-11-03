package coverletter.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import coverletter.Prompt;
import coverletter.config.AppConfig;

/**
 * Gemini generateContent REST 직접 호출
 * 계층 : ① 모델 장애 조치 (429 소진 · 부재 -> 다음 후보) -> ② 동일 모델 429 백오프 (최대 2회)
 * 400/403은 모델과 무관 (키 · 프롬프트 문제) 해서 전환 없이 즉시 오류
 */
public class GeminiClient {

	private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
	private static final Gson GSON = new Gson();
	
	// HTTP 상태코드 기반 오류. statusCode 0 = 네트워크 / 기타
	public static class GeminiException extends RuntimeException {
		public final int statusCode;
		public GeminiException(int statusCode, String message) {
			super(message);
			this.statusCode = statusCode;
		}
	}
	
	// 전환 / 진행 알림 통로
	private final Consumer<String> status;
	
	public GeminiClient() {
		this(msg -> System.out.println("[Gemini] " + msg));
	}
	
	public GeminiClient(Consumer<String> status) {
		this.status = status;
	}
	
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	
	public String generate(AppConfig config, Prompt p) {
		GeminiException last = null;
		for (String model : config.models()) {
			try {
				return generateOnModel(model, config, p);
			} catch (GeminiException e) {
				if(e.statusCode == 429 || e.statusCode == 404) {
					last = e;
					status.accept((e.statusCode == 404 ? "모델 접근 불가(404): " : "무료 한도 도달(429): ")
							+ model + " - 다음 후보로 전환");
					continue;
				}
				throw e;				// 400/403 등 - 모델을 바꿔도 해결되지 않는 오류
			}
		}
		throw last != null ? last : new GeminiException(0, "시도 가능한 모델이 없습니다");
	}
	
	private String generateOnModel(String model, AppConfig config, Prompt p) {
		JsonObject body = new JsonObject();
		if(p.systemText() != null && !p.systemText().isBlank()) {
			body.add("system_instruction", textPart(p.systemText()));
		}
		JsonArray contents = new JsonArray();
		contents.add(textPart(p.userText()));
		body.add("contents", contents);
		JsonObject gen = new JsonObject();
		gen.addProperty("temperature", config.temperature());
		gen.addProperty("maxOutputTokens", config.maxOutputTokens());
		body.add("generationConfig", gen);
		
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(BASE + model + ":generateContent"))
				.timeout(Duration.ofSeconds(120))
				.header("x-goog-api-key", config.geminiApiKey())
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		
		int attempt = 0;
		while(true) {
			try {
				HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
				int st = res.statusCode();
				if (st == 200) return extractText(res.body());
				if (st == 429 && attempt < 2) {				// 순간 폭주용 백오프 - 일 누적 소진은 ① 장애조치 담당
					sleep(2_000L << attempt);
					attempt += 1;
					continue;
				}
				throw new GeminiException(st, httpMessage(st));
			} catch (GeminiException e) {
				throw e;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new GeminiException(0, "호출이 중단되었습니다.");
			} catch (Exception e) {
				throw new GeminiException(0, "네트워크 오류 : " + e.getMessage());
			}
		}
	}
	
	// 응답 구조 : candidates[].content.parts[*].text 이어붙이기
	private static String extractText(String json) {
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		if(root == null || !root.has("candidates")) return "";
		JsonArray candidates = root.getAsJsonArray("candidates");
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < candidates.size(); i++) {
			JsonObject content = candidates.get(i).getAsJsonObject().getAsJsonObject("content");
			if(content == null || !content.has("parts")) continue;
			JsonArray parts = content.getAsJsonArray("parts");
			for(int j = 0; j < parts.size(); j++) {
				JsonObject part = parts.get(j).getAsJsonObject();
				if(part.has("text")) sb.append(part.get("text").getAsString());
			}
		}
		return sb.toString();
	}
	
	private static JsonObject textPart(String text) {
		JsonObject part = new JsonObject();
		part.addProperty("text", text);
		JsonArray parts = new JsonArray();
		parts.add(part);
		JsonObject content = new JsonObject();
		content.add("parts", parts);
		return content;
	}
	
	private static String httpMessage(int status) {
		return switch (status) {
			case 400 -> "400 파라미터/프롬프트 오류";
			case 403 -> "403 키 유효성 오류 - 키/발급 상태 확인 필요";
			case 429 -> "429 무료 한도 도달 - 몇 분 후 재시도";
			case 404 -> "404 모델을 찾을 수 없음";
			default -> status + " 오류 - 잠시 후 재시도";
		};
	}
	
	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
