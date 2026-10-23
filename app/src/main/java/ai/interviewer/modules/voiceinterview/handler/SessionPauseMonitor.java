package ai.interviewer.modules.voiceinterview.handler;

import ai.interviewer.modules.voiceinterview.service.QwenAsrService;
import ai.interviewer.modules.voiceinterview.service.VoiceInterviewService;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话长时间无活动时的提醒与暂停。
 *
 * <p>从 WebSocket handler 里抽出来的原因：这段逻辑只关心「谁多久没说话」，
 * 与消息收发的协议细节无关，抽出来之后可以在没有真实连接的情况下用单测覆盖
 * 4:30 提醒与 5:00 暂停两条路径。
 */
@Slf4j
@RequiredArgsConstructor
class SessionPauseMonitor {

  private final Map<String, WebSocketSession> sessions;
  private final Map<String, SessionState> sessionStates;
  private final Map<String, Long> lastActivityTime;
  private final VoiceInterviewService interviewService;
  private final QwenAsrService sttService;
  private final VoiceMessageSender messageSender;
  private final long warningTimeMs;
  private final long pauseTimeoutMs;

  /**
   * 定时任务：检查暂停警告和超时，每 30 秒跑一次。
   */
  void checkPauseTimeout() {
    long now = System.currentTimeMillis();

    lastActivityTime.forEach((sessionId, lastTime) -> {
      long elapsed = now - lastTime;

      // Send warning at 4:30
      if (elapsed > warningTimeMs && elapsed < pauseTimeoutMs) {
        sendPauseWarning(sessionId);
      }
      // Timeout at 5:00
      else if (elapsed >= pauseTimeoutMs) {
        log.warn("Session {} inactive for {} minutes, pausing",
            sessionId, pauseTimeoutMs / 60000);
        handlePauseTimeout(sessionId);
      }
    });
  }

  /**
   * 定时任务：清理数据库里长期未结束的会话，每 60 秒跑一次。
   */
  void cleanupStaleSessions() {
    try {
      int cleaned = interviewService.cleanupStaleSessions();
      if (cleaned > 0) {
        log.info("Stale session cleanup: {} sessions cleaned", cleaned);
      }
    } catch (Exception e) {
      log.error("Error during stale session cleanup", e);
    }
  }

  /**
   * Send pause warning notification
   * 发送暂停警告通知
   */
  private void sendPauseWarning(String sessionId) {
    WebSocketSession session = sessions.get(sessionId);
    if (session != null && session.isOpen()) {
      messageSender.sendControl(session, "pause_timeout_warning", "会话将在30秒后暂停，请继续说话或点击继续");
    }
  }

  /**
   * Handle pause timeout - save state and disconnect
   * 处理暂停超时 - 保存状态并断开连接
   */
  private void handlePauseTimeout(String sessionId) {
    WebSocketSession session = sessions.get(sessionId);

    try {
      if (session != null && session.isOpen()) {
        messageSender.sendControl(session, "pause_timeout", "会话因超时已暂停,可在历史记录中恢复");
      }

      // 2. Save session state to database
      interviewService.pauseSession(sessionId, "timeout");

      // 3. Close WebSocket connection
      if (session != null && session.isOpen()) {
        session.close(CloseStatus.GOING_AWAY);
      }

      // 4. Cleanup - Stop ASR session to prevent resource leak
      sttService.stopTranscription(sessionId);
      sessions.remove(sessionId);
      sessionStates.remove(sessionId);
      lastActivityTime.remove(sessionId);

      log.info("Session {} paused due to timeout", sessionId);

    } catch (Exception e) {
      log.error("Error handling pause timeout for session {}", sessionId, e);
    }
  }
}
