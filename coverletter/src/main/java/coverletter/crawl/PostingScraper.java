package coverletter.crawl;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 공고 URL -> HTML 다운로드 -> 본문 텍스트 정제.
 * 실패 시 ScraperException (이 뒤의 붙여넣기 백업 모드가 대안)
 */
public class PostingScraper {

	// 크롤 실패 오류
	public static class ScraperException extends RuntimeException{
		public ScraperException(String message, Throwable cause) {
			super(message, cause);
		}
	}
	
	private static final int TIMEOUT_MS = 15000;
	
	public String fetch(String url) {
		Document doc;
		try {
			doc = Jsoup.connect(url)
					.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
							+ "(KHTML, like Gecko) Chrome/120 Safari/537.36")		// 브라우저 UA - 차단 완화
					.timeout(TIMEOUT_MS)
					.followRedirects(true)
					.get();
		} catch (IOException e) {
			throw new ScraperException("공고 페이지 열기 실패 : " + e.getMessage(), e);
		}
		
		// 내용물이 아닌 골격 요소 제거 후 텍스트 추출
		doc.select("script, style, nav, header, footer, noscript, aside, from").remove();
		return doc.text().strip();
	}
	
	// 빈 본문 / JS 렌더링 페이지 판정 - 200자 이하면 true
	public boolean looksEmpty(String text) {
		return text == null || text.strip().codePoints().count() <= 200;
	}
}
