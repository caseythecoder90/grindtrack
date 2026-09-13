package dev.grindtrack.recovery.service;

import dev.grindtrack.web.BadRequestException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * The lines of a PDF, page by page, with a mark on the lines that start a paragraph.
 *
 * <p>PDFBox finds paragraph starts by indentation, which is how a typeset book marks them; the mark
 * is a character the book itself will never contain. The publisher's PDFs set the "no copying"
 * permission bit with no user password; the bit is a request to viewers, and this is the owner
 * reading a book they have, so it is not honoured here.
 */
final class PdfText {

  /** Put at the front of a line that opens a paragraph. */
  static final char PARAGRAPH = '¶';

  private PdfText() {}

  static boolean isPdf(byte[] bytes) {
    return bytes.length > 4
        && bytes[0] == '%'
        && bytes[1] == 'P'
        && bytes[2] == 'D'
        && bytes[3] == 'F';
  }

  /** One list of lines per page, in reading order. */
  static List<List<String>> pages(byte[] bytes) {
    try (PDDocument doc = Loader.loadPDF(bytes)) {
      doc.setAllSecurityToBeRemoved(true);
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      stripper.setParagraphStart(String.valueOf(PARAGRAPH));
      stripper.setIndentThreshold(1.5f);
      List<List<String>> pages = new ArrayList<>();
      for (int p = 1; p <= doc.getNumberOfPages(); p++) {
        stripper.setStartPage(p);
        stripper.setEndPage(p);
        List<String> lines = new ArrayList<>();
        for (String line : stripper.getText(doc).split("\\r?\\n")) {
          if (!line.isBlank()) {
            lines.add(line);
          }
        }
        pages.add(lines);
      }
      return pages;
    } catch (IOException e) {
      throw new BadRequestException("That PDF could not be read: " + e.getMessage());
    }
  }
}
