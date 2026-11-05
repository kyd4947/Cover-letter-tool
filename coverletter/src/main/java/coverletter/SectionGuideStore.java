package coverletter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 항목별 작성법 가이드 로더
 * classpath 번들 (resources/guides/{항목명}.md)만 사용, 부재 항목은 _default.md
 * 파일명 규약 : 항목명의 공백은 '_'로 치환
 */
public class SectionGuideStore {

	// fallback=true 이면 전용 가이드가 없어 _default.md 로 대체된 것
	public record SectionGuide(String sectionTitle, String writingGuide, boolean fallback) {}
	
	// 항목명 -> resources/guides/{항목명}.md 로딩, 없으면 _default.md
	public SectionGuide get(String sectionTitle) throws IOException {
			byte[] guide = read(guidePath(sectionTitle));
			if(guide != null) {
				return new SectionGuide(sectionTitle, text(guide), false);
			}
			byte[] fallback = read("/guides/_default.md");
			if(fallback == null) {
				throw new IOException("guides 번들 누락 : 기본 지침 _default.md 가 없습니다.");
			}
			return new SectionGuide(sectionTitle, text(fallback), true);
	}
	
	private static String guidePath(String sectionTitle) {
		return "/guides/" + sectionTitle.strip().replace(' ', '_') + ".md";
	}
	
	private static String text(byte[] bytes) {
		return new String(bytes, StandardCharsets.UTF_8).strip();
	}
	
	// 리소스 부재면 null. 읽기 실패는 조용히 대체하지 않고 그대로 IOException
	private static byte[] read(String path) throws IOException {
		InputStream in = SectionGuideStore.class.getResourceAsStream(path);
		if (in == null) {
			return null;
		}
		try (InputStream stream = in) {
			return stream.readAllBytes();
		}
	}
}
