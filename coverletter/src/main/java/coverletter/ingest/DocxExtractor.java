package coverletter.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

// docx 내부 : zip 안 word/document.xml - 문단 순으로 글자만 뽑아 평문화
public class DocxExtractor implements Extractor {

	@Override
	public String extract(Path file) throws IOException{
		try(ZipFile zip = new ZipFile(file.toFile())) {
			ZipEntry entry = zip.getEntry("word/document.xml");
			if (entry == null) {
				throw new Extractor.ExtractException("docx 형식 아님 (word/document.xml 없음) : " + file, null);
			}
			try (InputStream in = zip.getInputStream(entry)){
				Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
				StringBuilder text = new StringBuilder();
				
				NodeList paragraphs = document.getElementsByTagNameNS("*", "p");		// 모든 문단
				for(int i = 0; i < paragraphs.getLength(); i++) {
					StringBuilder line = new StringBuilder();
					NodeList pieces = ((Element) paragraphs.item(i)).getElementsByTagNameNS("*", "t");		// 문단 안 글자 단위
					for(int j = 0; j < pieces.getLength(); j++) {
						line.append(pieces.item(j).getTextContent());
					}
					if (!line.isEmpty()) {
						text.append(line).append('\n');
					}
				}
				return text.toString().strip();
			} catch (Extractor.ExtractException e) {
				throw e;
			} catch (Exception e) {
				throw new Extractor.ExtractException("docx 파싱 실패: " + e.getMessage(), e);
			}
		}
	}
}
