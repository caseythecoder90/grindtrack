package dev.grindtrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.domain.UserRepository;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ConflictException;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A partner is made with a fresh secret and a hashed password; the owner's row is never touched.
 */
class UserServiceTest {

  private static final String PASSWORD = "twelve characters or more";

  private UserRepository users;
  private PasswordEncoder encoder;
  private UserService service;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    encoder = mock(PasswordEncoder.class);
    when(encoder.encode(any())).thenAnswer(inv -> "hash(" + inv.getArgument(0) + ")");
    when(users.save(any()))
        .thenAnswer(
            inv -> {
              User row = inv.getArgument(0);
              if (row.getId() == null) {
                ReflectionTestUtils.setField(row, "id", 2L);
              }
              return row;
            });
    service = new UserService(users, encoder, new TotpService());
  }

  private static User account(long id, String username, Role role) {
    User user = new User(username, "old-hash", "SECRET", role);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  @Test
  void createsAPartnerWithAHashedPasswordAndASecretItHandsBackOnce() {
    when(users.findByUsername("wife")).thenReturn(Optional.empty());

    UserService.Created created = service.createPartner("  wife ", PASSWORD);

    User user = created.user();
    assertThat(user.getId()).isEqualTo(2L);
    assertThat(user.getUsername()).isEqualTo("wife");
    assertThat(user.getRole()).isEqualTo(Role.PARTNER);
    assertThat(user.getPasswordHash()).isEqualTo("hash(" + PASSWORD + ")");
    assertThat(created.totpSecret()).isEqualTo(user.getTotpSecret()).isNotBlank();
    assertThat(created.otpauthUri()).contains("wife").contains(created.totpSecret());
  }

  @Test
  void aTakenNameIsAConflict() {
    when(users.findByUsername("casey")).thenReturn(Optional.of(account(1L, "casey", Role.OWNER)));

    assertThatThrownBy(() -> service.createPartner("casey", PASSWORD))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("casey");
    verify(users, never()).save(any());
  }

  @Test
  void aNameOrPasswordThatWillNotDoIsA400BeforeAnythingIsSaved() {
    assertThatThrownBy(() -> service.createPartner("  ", PASSWORD))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("username");
    assertThatThrownBy(() -> service.createPartner("two words", PASSWORD))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("spaces");
    assertThatThrownBy(() -> service.createPartner("x".repeat(65), PASSWORD))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("64");
    assertThatThrownBy(() -> service.createPartner("wife", "elevenchars"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("12");
    assertThatThrownBy(() -> service.createPartner("wife", null))
        .isInstanceOf(BadRequestException.class);
    verify(users, never()).save(any());
    verify(encoder, never()).encode(any());
  }

  @Test
  void resetsAPartnersPasswordAndNothingElseAboutThem() {
    User partner = account(2L, "wife", Role.PARTNER);
    when(users.findById(2L)).thenReturn(Optional.of(partner));

    service.resetPassword(2L, "a brand new passphrase");

    assertThat(partner.getPasswordHash()).isEqualTo("hash(a brand new passphrase)");
    assertThat(partner.getTotpSecret()).isEqualTo("SECRET");
    assertThat(partner.getRole()).isEqualTo(Role.PARTNER);
    verify(users).save(partner);
  }

  @Test
  void theOwnersAccountIsNotManagedFromHere() {
    when(users.findById(1L)).thenReturn(Optional.of(account(1L, "casey", Role.OWNER)));

    assertThatThrownBy(() -> service.partner(1L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("owner");
    assertThatThrownBy(() -> service.resetPassword(1L, PASSWORD))
        .isInstanceOf(BadRequestException.class);
    verify(users, never()).save(any());
  }

  @Test
  void anUnknownAccountIsA404() {
    when(users.findById(9L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.partner(9L)).isInstanceOf(NoSuchElementException.class);
  }
}
