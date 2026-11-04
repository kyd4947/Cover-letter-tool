package coverletter.profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * "## 제목# 라인 기준으로 프로젝트 섹션을 분리
 * rawMarkdown 은 원문 통째 (PromptBuilder 가 그대로 LLM 에 주입)
 */
public class ProfileStore {

	// 사용자 경력 전체 - 원문 + "## 제목" 단위로 잘라낸 프로젝트 목록
	public record Profile(String rawMarkdown, List<Project> projects) {}
	
	// 프로젝트 1개 = "## 제목" 섹션 (제목 + 본문)
	public record Project(String title, String body) {}
	
	// profile.md 를 읽어 Profile 로 파싱 (UTF-8)
	public Profile load(Path path) throws IOException{
		String raw = Files.readString(path).strip();
		
		List<Project> projects = new ArrayList<>();
		List<String> lines = raw.lines().toList();
		
		int i = 0;
		// 첫 "## " 위치로 진행 - 그 위의 문서 제목(# ....) 서문은 섹션 밖
		while(i < lines.size() && !lines.get(i).startsWith("## ")) {
			i += 1;
		}
		
		while(i < lines.size()) {
			String title = lines.get(i).substring(3).strip();			// "## " 3글자 떼고 제목
			i += 1;
			
			StringBuilder body = new StringBuilder();
			while(i < lines.size() && !lines.get(i).startsWith("## ")) {
				body.append(lines.get(i)).append("\n");
				i += 1;
			}
			projects.add(new Project(title, body.toString().strip()));
		}
		
		return new Profile(raw, projects);
	}
}
