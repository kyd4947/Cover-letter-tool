package coverletter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 항목별 작성법 가이드 로더
 * 우선순위 : 사용자 guides/{항목명}.md -> classpath 번들 (resources/guides/{항목명}.md) -> _default.md
 * 파일명 규약 : 항목명의 공백은 '_'로 치환
 * 첫 실행 시 번들 기본본을 실행 위치의 guides/ 로 복사 (사용자 수정 유지)
 */
public class SectionGuideStore {

	// 사용자 튜닝 폴더 - 실행 위치 기준 (config.json / profile.md 와 같은 계층)
	public static final Path USER_DIR = Path.of("guides");
	
	// 번들 기본 가이드 - 첫 실행 복사 대상
	private static final List<String> BUNDLED = List.of("성장과정", "지원동기", "성격_장단점", "입사후_포부", "_default");

	// fallback=true 이면 전용 가이드가 없어 _default.md 로 대체된 것
	public record SectionGuide(String sectionTitle, String writingGuide, boolean fallback) {}
	
	// 항목명 -> 사용자 guides/ -> classpath guides/ -> _default.md 순서
	public SectionGuide get(String sectionTitle) throws IOException {
			String fileName = fileName(sectionTitle);
			byte[] guide = readUser(fileName);
			if(guide == null) {
				guide = readClasspath("/guides/" + fileName);
			}
			if(guide != null) {
				return new SectionGuide(sectionTitle, text(guide), false);
			}
			byte[] fallback = readUser("_default.md");
			if(fallback == null) {
				fallback = readClasspath("/guides/_default.md");
			}
			if(fallback == null) {
				throw new IOException("guides 번들 누락 : 기본 지침 _default.md 가 없습니다.");
			}
			return new SectionGuide(sectionTitle, text(fallback), true);
	}
	
	// 첫 실행 - guides/ 가 없으면 번들 기본본 복사. 이미 있으면 사용자 수정 존중 (덮어쓰지 않음)
	public static void ensureUserGuides() throws IOException {
		if(Files.exists(USER_DIR)) {
			return;
		}
		Files.createDirectories(USER_DIR);
		for(String name : BUNDLED) {
			byte[] bundled = readClasspath("/guides/" + name + ".md");
			if(bundled != null) {
				Files.write(USER_DIR.resolve(name + ".md"), bundled);
			}
		}
	}
	
	private static String fileName(String sectionTitle) {
		return sectionTitle.strip().replace(' ', '_') + ".md";
	}
	
	// 사용자 파일 읽기 - 없으면 null, 읽기 실패는 그대로 IOException (조용히 무시하지 않음)
	private static byte[] readUser(String fileName) throws IOException {
		Path path = USER_DIR.resolve(fileName);
		return Files.exists(path) ? Files.readAllBytes(path) : null;
	}
	
	private static byte[] readClasspath(String path) throws IOException {
		InputStream in = SectionGuideStore.class.getResourceAsStream(path);
		if (in == null) {
			return null;
		}
		try (InputStream stream = in) {
			return stream.readAllBytes();
		}
	}
	
	private static String text(byte[] bytes) {
		return new String(bytes, StandardCharsets.UTF_8).strip();
	}
}
