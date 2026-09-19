package dev.grindtrack.auth.domain;

/**
 * Who an account is, and so what it may reach.
 *
 * <p>Two kinds. The owner is the account bootstrap made; everything in the app is theirs. A partner
 * is made by the owner from the app and reaches only the paths {@code SecurityConfig} lists for
 * both roles: the session endpoints, notifications, and next the chat. There is no third kind and
 * no permissions table. The split is by URL, in one place, and a new endpoint belongs to the owner
 * until it is named as shared.
 */
public enum Role {
  OWNER,
  PARTNER
}
