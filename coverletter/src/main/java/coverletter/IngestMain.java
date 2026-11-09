package coverletter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import coverletter.config.AppConfig;
import coverletter.ingest.Ingestor;
import coverletter.llm.GeminiClient;

// 적재 CLI - 실행 인수 : 원본 파일 경로 여러 개
public class IngestMain {

	public static void main(String[] args) {
		try {
			if(args.length < 1) {
				System.err.println("사용법 : IngestMain<자료파일...> (공백 포함 경로는 따옴표)");
				return;
			}
			AppConfig config = AppConfig.load();
			System.out.println("키 로딩 OK - 모델 : " + config.model());
			
			List<Path> files = Arrays.stream(args).map(Path::of).filter(Files::exists).toList();
			if(files.isEmpty()) {
				System.err.println("존재하는 파일이 없습니다: " + Arrays.toString(args));
				return;
			}
			new Ingestor().ingest(config, files);
			System.out.println("Ingest 완료");
		} catch(GeminiClient.GeminiException e) {
			System.err.println("[호출 실패] " + e.getMessage());
		} catch(IllegalStateException e) {
			System.err.println("[설정 문제] " + e.getMessage());
		} catch(Exception e) {
			System.err.println("[오류] " + e.getMessage());
		}
	}
}
