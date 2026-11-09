package coverletter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import coverletter.config.AppConfig;
import coverletter.ingest.Extractors;
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
			
			List<Path> files = new ArrayList<>();
			for(String arg : args) {
				if(arg.startsWith("http")) {
					System.err.println("[안내] '" + arg + "' 는 URL - 적재는 문서 파일 경로만");
					continue;
				}
				Path path = Path.of(arg);
				if(Files.isDirectory(path)) {
					try(var entries = Files.list(path)){
						// 지원 확장자만, 바로 아래 1층만 (재귀 X), STORE 자기 자신 제외
						List<Path> found = entries.filter(Extractors::supported)
								.filter(f -> !f.getFileName().toString().equalsIgnoreCase("profile.md"))
								.toList();
						System.out.println("[폴더] " + path.getFileName() + "- 지원 파일 " + found.size() + "개");
						files.addAll(found);
					}
					continue;
				}
				if (!Files.exists(path)) {
					System.err.println("[스킵] 존재하지 않는 경로 : " + arg);
					continue;
				}
				files.add(path);
			}
			if (files.isEmpty()) {
				System.err.println("적재할 파일이 없습니다.");
				return;
			}
		} catch(GeminiClient.GeminiException e) {
			System.err.println("[호출 실패] " + e.getMessage());
		} catch(IllegalStateException e) {
			System.err.println("[설정 문제] " + e.getMessage());
		} catch(Exception e) {
			System.err.println("[오류] " + e.getMessage());
		}
	}
}
