package coverletter;

import coverletter.config.AppConfig;
import coverletter.llm.GeminiClient;

public class Main {
	public static void main(String[] args) {
		try {
			AppConfig config = AppConfig.load();
			System.out.println("키 로딩 OK - 모델 : " + config.model());
			
			GeminiClient gemini = new GeminiClient();
			Prompt test = new Prompt(
					"너는 한국 채용 자기소개서 작성 보조 에이전트야.",
					"너를 한 줄로 소개하라. '연결 성공'을 반드시 포함할 것.");
			String answer = gemini.generate(config, test);
			
			System.out.println(answer);
			System.out.println("GeminiClient 동작 성공 !");
		} catch(GeminiClient.GeminiException e) {
			System.err.println("[호출 실패] " + e.getMessage());
		} catch(IllegalStateException e) {
			System.err.println("[설정 문제] " + e.getMessage());
		}
	}
}
