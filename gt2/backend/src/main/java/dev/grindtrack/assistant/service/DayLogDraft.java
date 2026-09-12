package dev.grindtrack.assistant.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * A day's log as drafted in conversation — only the parts that were said.
 *
 * <p>Every field is nullable and null means <em>leave what is there</em>. That is the whole
 * contract, and it is what makes a draft safe to accept over a day that already has an entry:
 * "logged two hours on etcd" must not blank the wins written this morning. The merge happens at
 * accept time against the day as it is then, not as it was when the draft was made.
 */
public record DayLogDraft(
    BigDecimal hours,
    List<String> categories,
    String focus,
    String did,
    String wins,
    String blockers,
    Integer energy) {}
