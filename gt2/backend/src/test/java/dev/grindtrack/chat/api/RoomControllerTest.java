package dev.grindtrack.chat.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.security.SignedIn;
import dev.grindtrack.chat.domain.MediaKind;
import dev.grindtrack.chat.service.MediaService;
import dev.grindtrack.chat.service.RoomService;
import dev.grindtrack.web.ApiExceptionHandler;
import dev.grindtrack.web.ServiceOffException;
import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The shapes over HTTP, and that every one of them is asked on behalf of the signed-in account. */
class RoomControllerTest {

  private static final SignedIn CASEY = new SignedIn(1L, "casey", Role.OWNER);
  private static final UUID CLIENT_ID = UUID.fromString("6f1d2b6e-1c3a-4f9e-9b2a-1d2e3f4a5b6c");

  private RoomService chat;
  private MediaService media;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    chat = mock(RoomService.class);
    media = mock(MediaService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new RoomController(chat, media))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  private static RoomService.MessageView view(long id, String body) {
    return new RoomService.MessageView(
        id, 1L, body, "2026-09-19T08:00:00Z", null, CLIENT_ID.toString(), List.of(), null);
  }

  private static MediaService.MediaView picture(long id) {
    return new MediaService.MediaView(
        id, MediaKind.IMAGE, "image/jpeg", 1000, 1600, 1200, null, true, false);
  }

  @Test
  void theStateIsTheRoomAsSeenByMe() throws Exception {
    when(chat.state(CASEY))
        .thenReturn(
            new RoomService.State(
                new RoomService.Person(1L, "casey"),
                new RoomService.Person(2L, "wife"),
                3,
                10,
                new RoomService.CursorView(1L, 8, 4),
                new RoomService.CursorView(2L, 9, 9)));

    mvc.perform(get("/api/chat").principal(CASEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.them.username").value("wife"))
        .andExpect(jsonPath("$.unread").value(3))
        .andExpect(jsonPath("$.latestId").value(10))
        .andExpect(jsonPath("$.theirs.readId").value(9));
  }

  @Test
  void historyPassesBeforeAndAfterThrough() throws Exception {
    when(chat.history(5L, null)).thenReturn(new RoomService.Page(List.of(view(4, "a")), false));

    mvc.perform(get("/api/chat/messages").param("before", "5").principal(CASEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[0].id").value(4))
        .andExpect(jsonPath("$.hasMore").value(false));
    verify(chat).history(5L, null);

    when(chat.history(null, 9L)).thenReturn(new RoomService.Page(List.of(), false));
    mvc.perform(get("/api/chat/messages").param("after", "9").principal(CASEY))
        .andExpect(status().isOk());
    verify(chat).history(null, 9L);
  }

  @Test
  void sendingTakesAClientIdAndSomeWords() throws Exception {
    when(chat.send(CASEY, CLIENT_ID, "hello", null)).thenReturn(view(10, "hello"));

    mvc.perform(
            post("/api/chat/messages")
                .principal(CASEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"body\":\"  hello \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.body").value("hello"));
  }

  @Test
  void withAPictureTheWordsMayBeNone() throws Exception {
    when(chat.send(CASEY, CLIENT_ID, "", 4L)).thenReturn(view(10, ""));

    mvc.perform(
            post("/api/chat/messages")
                .principal(CASEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"body\":\"\",\"mediaId\":4}"))
        .andExpect(status().isOk());
    verify(chat).send(CASEY, CLIENT_ID, "", 4L);
  }

  @Test
  void aBadClientIdOrNoWordsIsA400WithASentence() throws Exception {
    mvc.perform(
            post("/api/chat/messages")
                .principal(CASEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"nope\",\"body\":\"hello\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error").value("clientId must be a UUID the phone made for this message"));

    mvc.perform(
            post("/api/chat/messages")
                .principal(CASEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"body\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("a message needs some words (max 4000 chars)"));
    verify(chat, never()).send(any(), any(), any(), any());
  }

  @Test
  void unsendingAnswersTheMessageAsItNowStandsAndSomebodyElsesIs404() throws Exception {
    when(chat.unsend(CASEY, 4L))
        .thenReturn(
            new RoomService.MessageView(
                4L, 1L, "", "2026-09-19T08:00:00Z", "2026-09-19T08:01:00Z", "x", List.of(), null));

    mvc.perform(delete("/api/chat/messages/4").principal(CASEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value(""))
        .andExpect(jsonPath("$.deletedAt").value("2026-09-19T08:01:00Z"));

    when(chat.unsend(CASEY, 9L)).thenThrow(new NoSuchElementException("message 9"));
    mvc.perform(delete("/api/chat/messages/9").principal(CASEY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not found: message 9"));
  }

  @Test
  void aReactionIsPutOnAndDeletedOffByItsEmojiInThePath() throws Exception {
    when(chat.react(eq(CASEY), eq(4L), eq("❤️"), any(Boolean.class))).thenReturn(view(4, "x"));

    mvc.perform(put("/api/chat/messages/4/reactions/{emoji}", "❤️").principal(CASEY))
        .andExpect(status().isOk());
    verify(chat).react(CASEY, 4L, "❤️", true);

    mvc.perform(delete("/api/chat/messages/4/reactions/{emoji}", "❤️").principal(CASEY))
        .andExpect(status().isOk());
    verify(chat).react(CASEY, 4L, "❤️", false);
  }

  @Test
  void theCursorTakesEitherNumber() throws Exception {
    when(chat.moveCursor(CASEY, null, 9L)).thenReturn(new RoomService.CursorView(1L, 9, 9));

    mvc.perform(
            post("/api/chat/cursor")
                .principal(CASEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"readUpTo\":9}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readId").value(9))
        .andExpect(jsonPath("$.deliveredId").value(9));
  }

  @Test
  void theMediaStatusSaysWhetherThereIsABucket() throws Exception {
    when(media.status()).thenReturn(new MediaService.Status(false, 104857600));

    mvc.perform(get("/api/chat/media/status").principal(CASEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(false))
        .andExpect(jsonPath("$.maxBytes").value(104857600));
  }

  @Test
  void anUploadIsAFileAPosterAndItsShape() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "cat.jpg", "image/jpeg", new byte[] {1, 2, 3});
    MockMultipartFile poster =
        new MockMultipartFile("poster", "poster.jpg", "image/jpeg", new byte[] {4});
    when(media.upload(eq(CASEY), any(), any(), eq(1600), eq(1200), isNull()))
        .thenReturn(picture(4));

    mvc.perform(
            multipart("/api/chat/media")
                .file(file)
                .file(poster)
                .param("width", "1600")
                .param("height", "1200")
                .principal(CASEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(4))
        .andExpect(jsonPath("$.kind").value("IMAGE"))
        .andExpect(jsonPath("$.hasPoster").value(true));
  }

  @Test
  void withNoBucketAnUploadIsA503WithTheSentence() throws Exception {
    when(media.upload(any(), any(), any(), any(), any(), any()))
        .thenThrow(new ServiceOffException("media is off — set MEDIA_S3_ENDPOINT …"));

    mvc.perform(
            multipart("/api/chat/media")
                .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {1}))
                .principal(CASEY))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("media is off — set MEDIA_S3_ENDPOINT …"));
  }

  @Test
  void theBytesAndThePosterAreA302ToASignedLinkNeverCached() throws Exception {
    when(media.link(4L, false)).thenReturn(URI.create("https://b.nbg1.example/chat/a.jpg?sig=1"));
    when(media.link(4L, true))
        .thenReturn(URI.create("https://b.nbg1.example/chat/a-poster.jpg?sig=2"));

    mvc.perform(get("/api/chat/media/4").principal(CASEY))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://b.nbg1.example/chat/a.jpg?sig=1"))
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(get("/api/chat/media/4/poster").principal(CASEY))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://b.nbg1.example/chat/a-poster.jpg?sig=2"));

    when(media.link(9L, false)).thenThrow(new NoSuchElementException("media 9"));
    mvc.perform(get("/api/chat/media/9").principal(CASEY)).andExpect(status().isNotFound());
  }

  @Test
  void theTrayIsListedAndAPictureGoesInAndOutOfIt() throws Exception {
    when(media.stickers()).thenReturn(List.of(picture(4)));
    mvc.perform(get("/api/chat/media/stickers").principal(CASEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(4));

    when(media.setSticker(4L, true)).thenReturn(picture(4));
    mvc.perform(put("/api/chat/media/4/sticker").principal(CASEY)).andExpect(status().isOk());
    verify(media).setSticker(4L, true);

    when(media.setSticker(4L, false)).thenReturn(picture(4));
    mvc.perform(delete("/api/chat/media/4/sticker").principal(CASEY)).andExpect(status().isOk());
    verify(media).setSticker(4L, false);
  }
}
