package dev.grindtrack.assistant.service;

import java.util.List;

/**
 * Todos drafted in conversation, waiting on a click.
 *
 * <p>A batch rather than one: "add three things" is one sentence and should be one card. Each item
 * is what the todo form takes — a title, a side of the day, an optional due date — and nothing the
 * model made up beyond that.
 *
 * @param dueDate ISO date or null; null means no deadline, never "today"
 */
public record TodoDraft(List<Item> items) {

  public record Item(String title, String kind, String dueDate) {}
}
