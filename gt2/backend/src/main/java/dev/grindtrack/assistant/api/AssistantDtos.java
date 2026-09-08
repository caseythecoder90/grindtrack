package dev.grindtrack.assistant.api;

/**
 * The assistant API's request and response shapes.
 *
 * <p>Thin: the context is a computed record already, so the response is that record rather than a
 * hand-copied projection of it. A DTO exists here for the same reason it does everywhere else — so
 * the wire shape is a named type — but there is nothing to map.
 */
public final class AssistantDtos {

  private AssistantDtos() {}

  /** What the read surface offers, so a client does not have to be told out of band. */
  public record ToolDescription(String name, String method, String path, String description) {}
}
