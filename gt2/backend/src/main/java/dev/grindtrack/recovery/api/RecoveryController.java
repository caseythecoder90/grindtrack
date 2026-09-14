package dev.grindtrack.recovery.api;

import dev.grindtrack.recovery.api.RecoveryDtos.ContactRequest;
import dev.grindtrack.recovery.api.RecoveryDtos.JournalRequest;
import dev.grindtrack.recovery.api.RecoveryDtos.JournalResponse;
import dev.grindtrack.recovery.api.RecoveryDtos.PersonRequest;
import dev.grindtrack.recovery.api.RecoveryDtos.PersonUpdateRequest;
import dev.grindtrack.recovery.api.RecoveryDtos.PlaceRequest;
import dev.grindtrack.recovery.api.RecoveryDtos.SessionRequest;
import dev.grindtrack.recovery.api.RecoveryDtos.SettingsRequest;
import dev.grindtrack.recovery.domain.PersonRole;
import dev.grindtrack.recovery.domain.TextSlot;
import dev.grindtrack.recovery.service.ImportReport;
import dev.grindtrack.recovery.service.RecoveryService;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.Requests;
import dev.grindtrack.web.Responses;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/recovery")
public class RecoveryController {

  /** Under the container's multipart limit; the whole book's PDFs come to a few megabytes. */
  private static final long MAX_BYTES = 5L * 1024 * 1024;

  private static final int MAX_FILES = 60;
  private static final int MAX_JOURNAL_CHARS = 20_000;
  private static final int MAX_TITLE_CHARS = 120;
  private static final int MAX_NAME_CHARS = 80;
  private static final int MAX_NOTE_CHARS = 300;
  private static final int MAX_MINUTES = 180;
  private static final int MAX_PAGES = 50;
  private static final int MAX_CADENCE_DAYS = 365;
  private static final int MAX_QUERY_CHARS = 120;

  private final RecoveryService recovery;

  public RecoveryController(RecoveryService recovery) {
    this.recovery = recovery;
  }

  @GetMapping("/today")
  public RecoveryService.Today today() {
    return recovery.today();
  }

  // ---- the reading ---------------------------------------------------------------------------

  @PostMapping("/read/done")
  public RecoveryService.Today finishReading() {
    return recovery.finishReading();
  }

  /** Read ahead and say so: everything up to this paragraph counts as read, today. */
  @PostMapping("/read/mark")
  public RecoveryService.Today markReadTo(@RequestParam int seq) {
    return recovery.markReadTo(seq);
  }

  @PostMapping("/read/catch-up")
  public RecoveryService.Today catchUp() {
    return recovery.catchUp();
  }

  @PostMapping("/read/restart")
  public RecoveryService.Today restartReading() {
    recovery.restartReading();
    return recovery.today();
  }

  /** The table of contents and where the cursor is, for reading anywhere. */
  @GetMapping("/book")
  public RecoveryService.Reading book() {
    RecoveryService.Reading reading = recovery.book();
    if (reading == null) {
      throw new NoSuchElementException("the book");
    }
    return reading;
  }

  @GetMapping("/book/chapters/{no}")
  public RecoveryService.ChapterText chapter(@PathVariable int no) {
    return recovery.chapter(no);
  }

  /** The book searched: forty hits at most, best first. Two characters is a word. */
  @GetMapping("/book/search")
  public List<RecoveryService.Hit> search(@RequestParam String q) {
    String query = Requests.requireText(q, "search needs a word", MAX_QUERY_CHARS);
    if (query.length() < 2) {
      throw new BadRequestException("search needs a word");
    }
    return recovery.search(query);
  }

  /** Where a printed page begins, for "go to page 58". */
  @GetMapping("/book/page/{label}")
  public RecoveryService.PagePlace page(@PathVariable String label) {
    return recovery.page(label).orElseThrow(() -> new NoSuchElementException("page " + label));
  }

  @PutMapping("/book/place")
  public Responses.Saved place(@RequestBody PlaceRequest body) {
    if (body.seq() == null || body.seq() < 0) {
      throw new BadRequestException("seq is required");
    }
    recovery.savePlace(body.seq());
    return Responses.Saved.of(body.seq());
  }

  @PutMapping("/settings")
  public RecoveryService.Settings settings(@RequestBody SettingsRequest body) {
    Integer pages = body.pagesPerDay();
    if (pages != null && (pages < 1 || pages > MAX_PAGES)) {
      throw new BadRequestException("pagesPerDay must be between 1 and " + MAX_PAGES);
    }
    return recovery.updateSettings(pages, minutes(body.meditationMinutes(), "meditationMinutes"));
  }

  @PostMapping("/sessions")
  public RecoveryService.Meditation session(@RequestBody SessionRequest body) {
    Integer minutes = minutes(body.minutes(), "minutes");
    if (minutes == null) {
      throw new BadRequestException("minutes is required");
    }
    return recovery.logSession(minutes, body.completed() == null || body.completed());
  }

  // ---- the journal ---------------------------------------------------------------------------

  @GetMapping("/journal")
  public List<JournalResponse> journal(@RequestParam(required = false) Long before) {
    return recovery.journal(before).stream().map(JournalResponse::from).toList();
  }

  @PostMapping("/journal")
  public JournalResponse addJournal(@RequestBody JournalRequest body) {
    String text =
        Requests.requireText(body.body(), "journal entry needs some words", MAX_JOURNAL_CHARS);
    return JournalResponse.from(recovery.addJournal(text, Boolean.TRUE.equals(body.spoken())));
  }

  @DeleteMapping("/journal/{id}")
  public Responses.Deleted deleteJournal(@PathVariable Long id) {
    if (!recovery.deleteJournal(id)) {
      throw new NoSuchElementException("journal entry " + id);
    }
    return Responses.Deleted.of(id);
  }

  // ---- the people ----------------------------------------------------------------------------

  @GetMapping("/people")
  public List<RecoveryService.PersonView> people() {
    return recovery.people();
  }

  @PostMapping("/people")
  public RecoveryService.PersonView addPerson(@RequestBody PersonRequest body) {
    String name = Requests.requireText(body.name(), "person needs a name", MAX_NAME_CHARS);
    PersonRole role = body.role() == null ? PersonRole.FRIEND : role(body.role());
    return recovery.addPerson(
        name,
        role,
        cadence(body.cadenceDays() == null ? 7 : body.cadenceDays()),
        Requests.optionalText(body.note(), "note", MAX_NOTE_CHARS));
  }

  @PatchMapping("/people/{id}")
  public RecoveryService.PersonView updatePerson(
      @PathVariable Long id, @RequestBody PersonUpdateRequest body) {
    String name =
        body.name() == null
            ? null
            : Requests.requireText(body.name(), "person needs a name", MAX_NAME_CHARS);
    return recovery
        .updatePerson(
            id,
            name,
            body.role() == null ? null : role(body.role()),
            body.cadenceDays() == null ? null : cadence(body.cadenceDays()),
            Requests.optionalText(body.note(), "note", MAX_NOTE_CHARS),
            Boolean.TRUE.equals(body.clearNote()))
        .orElseThrow(() -> new NoSuchElementException("person " + id));
  }

  @DeleteMapping("/people/{id}")
  public Responses.Deleted archivePerson(@PathVariable Long id) {
    if (!recovery.archivePerson(id)) {
      throw new NoSuchElementException("person " + id);
    }
    return Responses.Deleted.of(id);
  }

  /** A call logged. For someone still to be asked, this is the asking, and the answer was yes. */
  @PostMapping("/people/{id}/contacts")
  public RecoveryService.PersonView logContact(
      @PathVariable Long id, @RequestBody(required = false) ContactRequest body) {
    String note = body == null ? null : Requests.optionalText(body.note(), "note", MAX_NOTE_CHARS);
    return recovery
        .logContact(id, note)
        .orElseThrow(() -> new NoSuchElementException("person " + id));
  }

  @GetMapping("/people/{id}/contacts")
  public List<RecoveryService.ContactView> contacts(@PathVariable Long id) {
    return recovery.contacts(id);
  }

  // ---- the books -----------------------------------------------------------------------------

  @GetMapping("/library")
  public RecoveryService.Library library() {
    return recovery.library();
  }

  /**
   * The book's files, all at once: the publisher's PDFs, or one plain-text file. A dry run reads
   * them and reports; the real run replaces the slot and keeps the files. Nothing is written to
   * disk.
   */
  @PostMapping("/import/{slot}")
  public ImportReport importFiles(
      @PathVariable String slot,
      @RequestParam(defaultValue = "true") boolean dryRun,
      @RequestParam(required = false) String title,
      @RequestParam("files") List<MultipartFile> files)
      throws IOException {
    TextSlot target = slot(slot);
    if (files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
      throw new BadRequestException("Choose at least one file.");
    }
    if (files.size() > MAX_FILES) {
      throw new BadRequestException("That is more than " + MAX_FILES + " files — is it one book?");
    }
    List<RecoveryService.Upload> uploads = new ArrayList<>();
    for (MultipartFile f : files) {
      if (f.isEmpty()) {
        continue;
      }
      if (f.getSize() > MAX_BYTES) {
        throw new BadRequestException(f.getOriginalFilename() + " is larger than 5 MB.");
      }
      String name = f.getOriginalFilename() == null ? "file" : f.getOriginalFilename();
      uploads.add(new RecoveryService.Upload(name, f.getBytes()));
    }
    String name = Requests.optionalText(title, "title", MAX_TITLE_CHARS);
    return recovery.importFiles(target, name, uploads, dryRun);
  }

  /** The stored files through the parser again. */
  @PostMapping("/import/{slot}/reparse")
  public ImportReport reparse(@PathVariable String slot) {
    return recovery.reparse(slot(slot));
  }

  @PostMapping("/bible/restart")
  public RecoveryService.Library restartBible() {
    recovery.restartBiblePlan();
    return recovery.library();
  }

  private static TextSlot slot(String value) {
    try {
      return TextSlot.of(value);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("slot must be big_book, reflection or meditation");
    }
  }

  private static PersonRole role(String value) {
    try {
      return PersonRole.of(value);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("role must be sponsor, prospect or friend");
    }
  }

  private static int cadence(int days) {
    if (days < 1 || days > MAX_CADENCE_DAYS) {
      throw new BadRequestException("cadenceDays must be between 1 and " + MAX_CADENCE_DAYS);
    }
    return days;
  }

  private static Integer minutes(Integer value, String field) {
    if (value == null) {
      return null;
    }
    if (value < 1 || value > MAX_MINUTES) {
      throw new BadRequestException(field + " must be between 1 and " + MAX_MINUTES);
    }
    return value;
  }
}
