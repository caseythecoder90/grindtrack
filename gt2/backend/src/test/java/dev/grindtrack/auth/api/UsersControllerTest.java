package dev.grindtrack.auth.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.service.AuthService;
import dev.grindtrack.auth.service.TrustedDeviceService;
import dev.grindtrack.auth.service.UserService;
import dev.grindtrack.web.ApiExceptionHandler;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ConflictException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The shapes: a list that never carries a hash, a creation that carries the secret once. */
class UsersControllerTest {

  private UserService userService;
  private AuthService authService;
  private TrustedDeviceService trustedDevices;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    userService = mock(UserService.class);
    authService = mock(AuthService.class);
    trustedDevices = mock(TrustedDeviceService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new UsersController(userService, authService, trustedDevices))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  private static User account(long id, String username, Role role) {
    User user = new User(username, "hash", "SECRET", role);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  @Test
  void theListIsNamesAndRolesAndNeverAHashOrASecret() throws Exception {
    when(userService.list())
        .thenReturn(List.of(account(1L, "casey", Role.OWNER), account(2L, "wife", Role.PARTNER)));

    mvc.perform(get("/api/auth/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].username").value("casey"))
        .andExpect(jsonPath("$[0].role").value("OWNER"))
        .andExpect(jsonPath("$[1].role").value("PARTNER"))
        .andExpect(jsonPath("$[1].passwordHash").doesNotExist())
        .andExpect(jsonPath("$[1].totpSecret").doesNotExist());
  }

  @Test
  void creatingAPartnerAnswersWithTheSecretOnce() throws Exception {
    User made = account(2L, "wife", Role.PARTNER);
    when(userService.createPartner("wife", "twelve characters or more"))
        .thenReturn(
            new UserService.Created(made, "JBSWY3DPEHPK3PXP", "otpauth://totp/grindtrack:wife?x"));

    mvc.perform(
            post("/api/auth/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"wife\",\"password\":\"twelve characters or more\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(2))
        .andExpect(jsonPath("$.username").value("wife"))
        .andExpect(jsonPath("$.role").value("PARTNER"))
        .andExpect(jsonPath("$.totpSecret").value("JBSWY3DPEHPK3PXP"))
        .andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/grindtrack:wife?x"));
  }

  @Test
  void aTakenNameIsA409WithASentence() throws Exception {
    when(userService.createPartner("casey", "twelve characters or more"))
        .thenThrow(new ConflictException("there is already an account called casey"));

    mvc.perform(
            post("/api/auth/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"casey\",\"password\":\"twelve characters or more\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("there is already an account called casey"));
  }

  @Test
  void resettingAPasswordAnswersWithTheAccount() throws Exception {
    when(userService.partner(2L)).thenReturn(account(2L, "wife", Role.PARTNER));

    mvc.perform(
            put("/api/auth/users/2/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"a brand new passphrase\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("wife"));
    verify(userService).resetPassword(2L, "a brand new passphrase");
  }

  @Test
  void signingAPartnerOutEverywhereEndsSessionsAndForgetsDevices() throws Exception {
    when(userService.partner(2L)).thenReturn(account(2L, "wife", Role.PARTNER));
    when(authService.revokeAllForUser(2L)).thenReturn(2);

    mvc.perform(post("/api/auth/users/2/logout-all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("logged out everywhere"))
        .andExpect(jsonPath("$.sessionsEnded").value(2));
    verify(trustedDevices).forgetAll(2L);
  }

  @Test
  void theOwnersOwnIdIsA400() throws Exception {
    when(userService.partner(1L))
        .thenThrow(new BadRequestException("the owner's account is not managed from here"));

    mvc.perform(post("/api/auth/users/1/logout-all"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("the owner's account is not managed from here"));
  }
}
