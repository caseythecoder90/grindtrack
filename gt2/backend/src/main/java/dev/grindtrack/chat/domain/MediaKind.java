package dev.grindtrack.chat.domain;

/**
 * What an upload is, decided by its content type. Stickers are images kept in the tray; a voice
 * message is audio the phone recorded.
 */
public enum MediaKind {
  IMAGE,
  VIDEO,
  AUDIO
}
