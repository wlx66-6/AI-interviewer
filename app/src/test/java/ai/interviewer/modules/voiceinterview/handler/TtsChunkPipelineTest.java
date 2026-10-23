package ai.interviewer.modules.voiceinterview.handler;

import ai.interviewer.modules.voiceinterview.service.QwenTtsService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("分句 TTS 管线的顺序保证")
class TtsChunkPipelineTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @AfterEach
  void shutdownExecutor() {
    executor.shutdownNow();
  }

  @Test
  @DisplayName("后一句先合成完也要等前一句发出去")
  void chunksAreSentInSentenceOrder() throws Exception {
    QwenTtsService ttsService = mock(QwenTtsService.class);
    when(ttsService.synthesize(any())).thenAnswer(invocation -> {
      String sentence = invocation.getArgument(0);
      // 第一句故意慢，如果实现没有按 index 排队，第二句会先发出去
      if ("坏".equals(sentence)) {
        Thread.sleep(200);
      }
      return sentence.getBytes(StandardCharsets.UTF_8);
    });
    WebSocketSession session = openSession();
    VoiceMessageSender sender = new VoiceMessageSender(objectMapper);

    TtsChunkPipeline pipeline = new TtsChunkPipeline(
        "session-1", session, new Semaphore(2), 5L, ttsService, executor, sender);
    pipeline.submit("坏");
    pipeline.submit("好");
    pipeline.finish();

    assertThat(pipeline.awaitCompletion()).isEqualTo(2);

    List<Map<String, Object>> messages = capturedMessages(session, 3);
    assertThat(messages).extracting(m -> m.get("type"))
        .containsExactly("audio_chunk", "audio_chunk", "control");
    assertThat(messages.subList(0, 2)).extracting(m -> m.get("index"))
        .containsExactly(0, 1);
    assertThat(messages.get(2)).containsEntry("action", "audio_complete");
  }

  @Test
  @DisplayName("一句都没合成出来时不发 audio_complete")
  void noChunksMeansNoCompletionMessage() throws Exception {
    QwenTtsService ttsService = mock(QwenTtsService.class);
    when(ttsService.synthesize(any())).thenReturn(new byte[0]);
    WebSocketSession session = openSession();

    TtsChunkPipeline pipeline = new TtsChunkPipeline(
        "session-2", session, new Semaphore(1), 5L, ttsService,
        executor, new VoiceMessageSender(objectMapper));
    pipeline.submit("空");
    pipeline.finish();

    assertThat(pipeline.awaitCompletion()).isZero();
    assertThat(messagesSoFar(session)).isEmpty();
  }

  private WebSocketSession openSession() throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> capturedMessages(WebSocketSession session, int expected)
      throws Exception {
    ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, timeout(3000).times(expected)).sendMessage(captor.capture());
    List<Map<String, Object>> payloads = new ArrayList<>();
    for (TextMessage message : captor.getAllValues()) {
      payloads.add(objectMapper.readValue(message.getPayload(), Map.class));
    }
    return payloads;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> messagesSoFar(WebSocketSession session) throws Exception {
    ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, timeout(500).times(0)).sendMessage(captor.capture());
    List<Map<String, Object>> payloads = new ArrayList<>();
    for (TextMessage message : captor.getAllValues()) {
      payloads.add(objectMapper.readValue(message.getPayload(), Map.class));
    }
    return payloads;
  }
}
