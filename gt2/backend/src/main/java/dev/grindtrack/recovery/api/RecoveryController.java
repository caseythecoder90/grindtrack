package dev.grindtrack.recovery.api;

import dev.grindtrack.recovery.api.RecoveryDtos.JournalRequest;
import dev.grindtrack.recovery.api.RecoveryDtos.JournalResponse;
import dev.grindtrack.recovery.api.RecoveryDtos.SessionRequest;
import dev.grindtrack.recovery.api.RecoveryDtos.SettingsRequest;
import dev.grindtrack.recovery.domain.TextSlot;
import dev.grindtrack.recovery.service.ImportReport;
import dev.grindtrack.recovery.service.RecoveryService;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.Requests;
import dev.grindtrack.web.Responses;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

  /** Under the container's multipart limit, and a whole book in plain text is well under this. */
  private static final long MAX_BYTES = 5L * 1024 * 1024;

  private static final int MAX_JOURNAL_CHARS = 20_000;
  private static final int MAX_TITLE_CHARS = 120;
  private static final int MAX_MINUTES = 180;

  private final RecoveryService recovery;

  public RecoveryController(RecoveryService recovery) {
    this.recovery = recovery;
  }

  @GetMapping("/today")
  public RecoveryService.Today today() {
    return recovery.today();
  }

  @PostMapping("/read/done")
  public RecoveryService.Today finishReading() {
    return recovery.finishReading();
  }

  @PostMapping("/read/restart")
  public RecoveryService.Today restartReading() {
    recovery.restartReading();
    return recovery.today();
  }

  @PutMapping("/settings")
  public RecoveryService.Settings settings(@RequestBody SettingsRequest body) {
    return recovery.updateSettings(
        minutes(body.readMinutes(), "readMinutes"),
        minutes(body.meditationMinutes(), "meditationMinutes"));
  }

  @PostMapping("/sessions")
  public RecoveryService.Meditation session(@RequestBody SessionRequest body) {
    Integer minutes = minutes(body.minutes(), "minutes");
    if (minutes == null) {
      throw new BadRequestException("minutes is required");
    }
    return recovery.logSession(minutes, body.completed() == null || body.completed());
  }

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

  @GetMapping("/library")
  public RecoveryService.Library library() {
    return recovery.library();
  }

  /**
   * The whole book as one plain-text file. A dry run reads it and reports; the real run replaces
   * the slot. The file is read into memory and never written to disk.
   */
  @PostMapping("/import/{slot}")
  public ImportReport importText(
      @PathVariable String slot,
      @RequestParam(defaultValue = "true") boolean dryRun,
      @RequestParam(required = false) String title,
      @RequestParam("file") MultipartFile file)
      throws IOException {
    TextSlot target = slot(slot);
    if (file.isEmpty()) {
      throw new BadRequestException("That file is empty.");
    }
    if (file.getSize() > MAX_BYTES) {
      throw new BadRequestException("That file is larger than 5 MB — is it really plain text?");
    }
    String content = new String(file.getBytes(), StandardCharsets.UTF_8);
    String name = Requests.optionalText(title, "title", MAX_TITLE_CHARS);
    return recovery.importText(target, name, content, dryRun);
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
