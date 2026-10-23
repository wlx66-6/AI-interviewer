package ai.interviewer.modules.voiceinterview.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("语音面试出站消息")
class VoiceMessageSenderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final VoiceMessageSender sender = new VoiceMessageSender(objectMapper);

  @Test
  @DisplayName("字幕消息带上 isFinal")
  void subtitleCarriesFinalFlag() throws Exception {
    WebSocketSession session = openSession();

    sender.sendSubtitle(session, "这是当前轮回答", true);

    Map<String, Object> payload = sentPayload(session);
    assertThat(payload).containsEntry("type", "subtitle");
    assertThat(payload).containsEntry("text", "这是当前轮回答");
    assertThat(payload).containsEntry("isFinal", true);
  }

  @Test
  @DisplayName("会话已关闭时不发送")
  void closedSessionReceivesNothing() throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(false);

    sender.sendText(session, "不该发出去");
    sender.sendError(session, "不该发出去");

    verify(session, never()).sendMessage(any());
  }

  @Test
  @DisplayName("control 消息带 action 和时间戳")
  void controlCarriesActionAndTimestamp() throws Exception {
    WebSocketSession session = openSession();

    sender.sendControl(session, "pause_timeout_warning", "会话将在30秒后暂停");

    Map<String, Object> payload = sentPayload(session);
    assertThat(payload).containsEntry("type", "control");
    assertThat(payload).containsEntry("action", "pause_timeout_warning");
    assertThat(payload).containsEntry("message", "会话将在30秒后暂停");
    assertThat(payload.get("timestamp")).isInstanceOf(Number.class);
  }

  private WebSocketSession openSession() throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> sentPayload(WebSocketSession session) throws Exception {
    ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(captor.capture());
    return objectMapper.readValue(captor.getValue().getPayload(), Map.class);
  }
}
