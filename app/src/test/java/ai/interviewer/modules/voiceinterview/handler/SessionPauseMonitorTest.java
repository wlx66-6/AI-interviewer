package ai.interviewer.modules.voiceinterview.handler;

import ai.interviewer.modules.voiceinterview.service.QwenAsrService;
import ai.interviewer.modules.voiceinterview.service.VoiceInterviewService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("会话暂停监控")
class SessionPauseMonitorTest {

  private static final String SESSION_ID = "session-1";
  private static final long WARNING_MS = 4L * 60 * 1000 + 30_000;
  private static final long TIMEOUT_MS = 5L * 60 * 1000;

  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();
  private final Map<String, Long> lastActivityTime = new ConcurrentHashMap<>();
  private final VoiceInterviewService interviewService = mock(VoiceInterviewService.class);
  private final QwenAsrService sttService = mock(QwenAsrService.class);
  private final VoiceMessageSender messageSender = mock(VoiceMessageSender.class);

  private WebSocketSession session;
  private SessionPauseMonitor monitor;

  @BeforeEach
  void setUp() throws Exception {
    session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);
    sessions.put(SESSION_ID, session);
    sessionStates.put(SESSION_ID, new SessionState());
    monitor = new SessionPauseMonitor(
        sessions, sessionStates, lastActivityTime,
        interviewService, sttService, messageSender, WARNING_MS, TIMEOUT_MS);
  }

  @Test
  @DisplayName("无活动超过 4 分 30 秒先提醒，不暂停")
  void warnsBeforeTimeout() {
    lastActivityTime.put(SESSION_ID, System.currentTimeMillis() - WARNING_MS - 5_000);

    monitor.checkPauseTimeout();

    verify(messageSender).sendControl(eq(session), eq("pause_timeout_warning"), anyString());
    verify(interviewService, never()).pauseSession(anyString(), anyString());
  }

  @Test
  @DisplayName("无活动超过 5 分钟才真正暂停并清理三张表")
  void pausesAfterTimeout() throws Exception {
    lastActivityTime.put(SESSION_ID, System.currentTimeMillis() - TIMEOUT_MS - 1_000);

    monitor.checkPauseTimeout();

    verify(messageSender).sendControl(eq(session), eq("pause_timeout"), anyString());
    verify(interviewService).pauseSession(SESSION_ID, "timeout");
    verify(session).close(CloseStatus.GOING_AWAY);
    verify(sttService).stopTranscription(SESSION_ID);
    assertThat(sessions).doesNotContainKey(SESSION_ID);
    assertThat(sessionStates).doesNotContainKey(SESSION_ID);
    assertThat(lastActivityTime).doesNotContainKey(SESSION_ID);
  }

  @Test
  @DisplayName("刚说过话的会话不做任何处理")
  void recentActivityIsIgnored() {
    lastActivityTime.put(SESSION_ID, System.currentTimeMillis());

    monitor.checkPauseTimeout();

    verifyNoInteractions(messageSender, interviewService, sttService);
  }

  @Test
  @DisplayName("数据库清理失败只记日志，不把异常抛给调度线程")
  void cleanupFailureIsSwallowed() {
    when(interviewService.cleanupStaleSessions()).thenThrow(new IllegalStateException("db down"));

    assertThatCode(monitor::cleanupStaleSessions).doesNotThrowAnyException();
  }
}
