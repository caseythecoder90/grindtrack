package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/** A PDF made here, so the extractor is tested on a real file with no book in it. */
class PdfTextTest {

  private static byte[] twoPages() throws Exception {
    try (PDDocument doc = new PDDocument()) {
      for (String[] lines :
          new String[][] {
            {"12 A TITLE", "    A first paragraph.", "and its second line."},
            {"13", "    Another page."}
          }) {
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
          cs.beginText();
          cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
          cs.newLineAtOffset(72, 700);
          for (String line : lines) {
            int indent = line.length() - line.stripLeading().length();
            cs.newLineAtOffset(indent * 6, 0);
            cs.showText(line.strip());
            cs.newLineAtOffset(-indent * 6, -16);
          }
          cs.endText();
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void pagesComeOutAsLinesWithParagraphStartsMarked() throws Exception {
    byte[] pdf = twoPages();

    assertThat(PdfText.isPdf(pdf)).isTrue();
    assertThat(PdfText.isPdf("JANUARY 1".getBytes())).isFalse();
    List<List<String>> pages = PdfText.pages(pdf);
    assertThat(pages).hasSize(2);
    assertThat(pages.get(0)).hasSize(3);
    assertThat(pages.get(0))
        .extracting(PdfBookParser::clean)
        .containsExactly("12 A TITLE", "A first paragraph.", "and its second line.");
    // The indentation is marked somewhere on the page; which line PDFBox picks for a page this
    // small is its business, and the parser's rules are tested on lines of their own.
    assertThat(pages.get(0)).anyMatch(l -> l.startsWith(String.valueOf(PdfText.PARAGRAPH)));
    assertThat(PdfBookParser.clean(pages.get(1).get(0))).isEqualTo("13");
  }
}
