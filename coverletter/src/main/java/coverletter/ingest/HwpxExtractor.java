package coverletter.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

// hwpx 내부 : zip 안 Contents/sectionN.xml - 문단 순으로 글자를 뽑아 평문화
public class HwpxExtractor implements Extractor{

	@Override
	public String extract(Path file) throws IOException {
		try (ZipFile zip = new ZipFile(file.toFile())){
			List<? extends ZipEntry> sections = zip.stream()
					.filter(entry -> entry.getName().matches("Contents/section\\d+\\.xml"))
					.sorted(Comparator.comparingInt(entry -> sectionNumber(entry.getName())))
					.toList();
			if (sections.isEmpty()) {
				throw new Extractor.ExtractException("hwpx 형식 아님 (섹션 없음): " + file, null);
			}
			
			StringBuilder text = new StringBuilder();
			for(ZipEntry section : sections) {
				try(InputStream in = zip.getInputStream(section)){
					Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
					
					// 문단 - 네임스페이스 와일드카드
					NodeList paragraphs = document.getElementsByTagNameNS("*", "p");
					for(int i =0; i < paragraphs.getLength(); i++) {
						StringBuilder line = new StringBuilder();
						NodeList pieces = ((Element) paragraphs.item(i)).getElementsByTagNameNS("*", "t");		// 문단 안 글자
						for (int j = 0; j < pieces.getLength(); j++) {
							line.append(pieces.item(j).getTextContent());
						}
						if (!line.isEmpty()) {
							text.append(line).append('\n');
						}
					}
				} catch(Extractor.ExtractException e) {
					throw e;
				} catch(Exception e) {
					throw new Extractor.ExtractException("hwpx 파싱 실패 : " + e.getMessage(), e);
				}
			}
			return text.toString().strip();
		}
	}
	
	private static int sectionNumber(String name) {
		return Integer.parseInt(name.replaceAll("\\D+", ""));
	}
}
