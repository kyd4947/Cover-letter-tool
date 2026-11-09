package coverletter.ingest;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

// pdf 텍스트 추출 - PDFBox 3.0.8
public class PdfExtractor implements Extractor{

	@Override
	public String extract(Path file) throws IOException{
		try(PDDocument document = Loader.loadPDF(file.toFile())){
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			return stripper.getText(document).strip();
		}
	}
}
