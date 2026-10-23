package ai.interviewer.modules.voiceinterview.handler;

import ai.interviewer.modules.voiceinterview.dto.WebSocketSubtitleMessage;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 语音面试所有出站消息的唯一出口。
 *
 * 抽出来之前这些方法散在 1400 行的 WebSocketHandler 里，每个方法各自处理 JSON 序列化失败和会话已关闭，
 * 写法还不一致：有的先判断 isOpen，有的依赖 sendMessage 内部判断。现在统一在这里判一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceMessageSender {

  private final ObjectMapper objectMapper;

  public void sendSubtitle(WebSocketSession session, String text, boolean isFinal) {
    WebSocketSubtitleMessage subtitle = WebSocketSubtitleMessage.builder()
        .type("subtitle")
        .text(text)
        .isFinal(isFinal)
        .build();
    send(session, subtitle);
  }

  public void sendText(WebSocketSession session, String text) {
    sendText(session, text, false);
  }

  public void sendText(WebSocketSession session, String text, boolean isFinal) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "text");
    payload.put("content", text);
    payload.put("final", isFinal);
    send(session, payload);
  }

  public void sendAudio(WebSocketSession session, byte[] audio, String text) {
    if (!isOpen(session)) {
      return;
    }
    String base64Audio = Base64.getEncoder().encodeToString(audio);
    log.info("Sending audio to frontend - WAV size: {} bytes, Base64 length: {}",
        audio.length, base64Audio.length());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "audio");
    payload.put("data", base64Audio);
    payload.put("text", text);
    send(session, payload);
  }

  public void sendAudioChunk(WebSocketSession session, byte[] wavAudio, int index, boolean isLast) {
    if (!isOpen(session)) {
      return;
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "audio_chunk");
    payload.put("data", Base64.getEncoder().encodeToString(wavAudio));
    payload.put("index", index);
    payload.put("isLast", isLast);
    send(session, payload);
    log.debug("[Session] Sent audio chunk index={}, isLast={}, size={} bytes",
        index, isLast, wavAudio.length);
  }

  public void sendAudioComplete(WebSocketSession session) {
    sendControl(session, "audio_complete", "面试官语音播放完成");
  }

  public void sendAsrReady(WebSocketSession session) {
    sendControl(session, "asr_ready", "语音识别已就绪");
  }

  public void sendAsrStatus(WebSocketSession session, String action, String message) {
    sendControl(session, action, message);
  }

  public void sendError(WebSocketSession session, String error) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "error");
    payload.put("message", error);
    send(session, payload);
  }

  /**
   * control 类消息：前端只认 action，不关心业务字段。
   */
  public void sendControl(WebSocketSession session, String action, String message) {
    if (!isOpen(session)) {
      return;
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "control");
    payload.put("action", action);
    payload.put("message", message);
    payload.put("timestamp", System.currentTimeMillis());
    send(session, payload);
  }

  private boolean isOpen(WebSocketSession session) {
    return session != null && session.isOpen();
  }

  private void send(WebSocketSession session, Object payload) {
    if (!isOpen(session)) {
      log.warn("Session is closed, cannot send message");
      return;
    }
    String json;
    try {
      json = objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      log.error("Error serializing message to JSON", e);
      return;
    }
    try {
      session.sendMessage(new TextMessage(json));
      log.debug("Message sent to session: length={}", json.length());
    } catch (Exception e) {
      log.error("Error sending message to session", e);
    }
  }
}
