package dev.grindtrack.recovery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.config.RecoveryProperties;
import dev.grindtrack.recovery.domain.BibleVerseRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * First start only: the verse table is empty, so it is filled from the resource. Thirty-one
 * thousand rows go in as batched inserts in a few seconds; through the entity, with identity ids,
 * they would be thirty-one thousand round trips. The table name carries the schema because the
 * template does not know Hibernate's default.
 */
@Component
public class BibleSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BibleSeeder.class);
  private static final int BATCH = 2000;
  private static final String INSERT =
      "insert into grindtrack.bible_verses (book, book_ord, chapter, verse, para, text)"
          + " values (?, ?, ?, ?, ?, ?)";

  private final BibleVerseRepository verses;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final RecoveryProperties props;
  private final BibleService bible;

  public BibleSeeder(
      BibleVerseRepository verses,
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      RecoveryProperties props,
      BibleService bible) {
    this.verses = verses;
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.props = props;
    this.bible = bible;
  }

  @Override
  public void run(ApplicationArguments args) throws IOException {
    if (verses.count() > 0) {
      return;
    }
    String file = props.bible() == null ? null : props.bible().file();
    if (file == null || file.isBlank()) {
      log.info("No Bible resource configured; the recovery tab has no passage.");
      return;
    }
    ClassPathResource resource = new ClassPathResource(file);
    if (!resource.exists()) {
      log.warn("Bible resource {} is missing; the recovery tab has no passage.", file);
      return;
    }
    long start = System.currentTimeMillis();
    int total = 0;
    try (BufferedReader in =
        new BufferedReader(
            new InputStreamReader(
                new GZIPInputStream(resource.getInputStream()), StandardCharsets.UTF_8))) {
      List<Object[]> batch = new ArrayList<>(BATCH);
      String line;
      while ((line = in.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        Object[] row = parse(mapper.readTree(line));
        if (row == null) {
          continue;
        }
        batch.add(row);
        if (batch.size() == BATCH) {
          jdbc.batchUpdate(INSERT, batch);
          total += batch.size();
          batch.clear();
        }
      }
      if (!batch.isEmpty()) {
        jdbc.batchUpdate(INSERT, batch);
        total += batch.size();
      }
    }
    log.info(
        "Seeded {} verses of the {} in {} ms",
        total,
        props.bible().name(),
        System.currentTimeMillis() - start);
    bible.forget();
  }

  /** {@code [book, chapter, verse, paragraph, text]}, or null for a book outside the canon. */
  static Object[] parse(JsonNode node) {
    String book = node.get(0).asText();
    int ord = BibleBooks.ord(book);
    if (ord == 0) {
      return null;
    }
    return new Object[] {
      book,
      ord,
      node.get(1).asInt(),
      node.get(2).asInt(),
      node.get(3).asInt() == 1,
      node.get(4).asText()
    };
  }
}
