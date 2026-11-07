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

// pptx 내부 : zip 안 ppt/slides/slideN.xml - 슬라이드 순서로 문단의 글자를 뽑아 평문화
public class PptxExtractor implements Extractor{

	@Override
	public String extract(Path file) throws IOException{
		try(ZipFile zip = new ZipFile(file.toFile())){
			List<? extends ZipEntry> slides = zip.stream()
					.filter(entry -> entry.getName().matches("ppt/slides/slide\\d+\\.xml"))
					.sorted(Comparator.comparingInt(entry -> slideNumber(entry.getName())))
					.toList();
			if(slides.isEmpty()) {
				throw new Extractor.ExtractException("pptx 형식 아님 (슬라이드 없음) : " + file, null);
			}
			
			StringBuilder text = new StringBuilder();
			for(ZipEntry slide : slides) {
				text.append("---- 슬라이드 ").append(slideNumber(slide.getName())).append(" ---- \n");
				try(InputStream in = zip.getInputStream(slide)){
					Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
					
					NodeList paragraphs = document.getElementsByTagNameNS("*", "p");			// 문단(a:p)
					for(int i = 0; i < paragraphs.getLength(); i++) {
						StringBuilder line = new StringBuilder();
						NodeList fragments = ((Element) paragraphs.item(i)).getElementsByTagNameNS("*", "t");
						for(int j = 0; j < fragments.getLength(); j++) {
							line.append(fragments.item(j).getTextContent());
						}
						if (!line.isEmpty()) {
							text.append(line).append("\n");
						}
					}
				} catch (Extractor.ExtractException e) {
					throw e;
				} catch (Exception e) {
					throw new Extractor.ExtractException("pptx 파싱 실패 : " + e.getMessage(), e);
				}
				text.append('\n');
			}
			return text.toString().strip();
		}
	}
	
	private static int slideNumber(String name) {
		return Integer.parseInt(name.replaceAll("\\D+", ""));
	}
}
