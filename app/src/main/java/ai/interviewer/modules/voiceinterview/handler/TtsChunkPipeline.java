package ai.interviewer.modules.voiceinterview.handler;

import ai.interviewer.modules.voiceinterview.audio.WavEncoder;
import ai.interviewer.modules.voiceinterview.service.QwenTtsService;

import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * 把 LLM 吐出来的句子按顺序合成语音并按顺序推给前端。
 *
 * 合成是并发的（受 ttsSemaphore 限流），发送必须严格按句子顺序，所以这里用 index 排队：
 * 后一句先合成完也要等前一句发出去。顺序错乱在前端表现为字幕和声音对不上。
 */
@Slf4j
class TtsChunkPipeline {

  private final String sessionId;
  private final WebSocketSession session;
  private final Semaphore ttsSemaphore;
  private final long ttsTimeoutSec;
  private final QwenTtsService ttsService;
  private final ExecutorService pipelineExecutor;
  private final VoiceMessageSender messageSender;

  private final Map<Integer, CompletableFuture<byte[]>> futures = new ConcurrentHashMap<>();
  private final AtomicInteger nextIndex = new AtomicInteger();
  private final AtomicInteger emittedChunks = new AtomicInteger();
  private final Object lock = new Object();
  private final CompletableFuture<Integer> completion;
  private volatile int totalChunks = -1;

  TtsChunkPipeline(
      String sessionId,
      WebSocketSession session,
      Semaphore ttsSemaphore,
      long ttsTimeoutSec,
      QwenTtsService ttsService,
      ExecutorService pipelineExecutor,
      VoiceMessageSender messageSender) {
    this.sessionId = sessionId;
    this.session = session;
    this.ttsSemaphore = ttsSemaphore;
    this.ttsTimeoutSec = ttsTimeoutSec;
    this.ttsService = ttsService;
    this.pipelineExecutor = pipelineExecutor;
    this.messageSender = messageSender;
    this.completion = CompletableFuture.supplyAsync(this::drainChunks, pipelineExecutor);
  }


  void submit(String sentence) {
    int index = nextIndex.getAndIncrement();
    ttsSemaphore.acquireUninterruptibly();
    CompletableFuture<byte[]> future = CompletableFuture.supplyAsync(() -> {
      try {
        return ttsService.synthesize(sentence);
      } finally {
        ttsSemaphore.release();
      }
    }, pipelineExecutor);

    futures.put(index, future);
    synchronized (lock) {
      lock.notifyAll();
    }
  }

  void finish() {
    synchronized (lock) {
      totalChunks = nextIndex.get();
      lock.notifyAll();
    }
  }

  int awaitCompletion() {
    long timeoutSec = Math.max(ttsTimeoutSec + 2, (ttsTimeoutSec + 1) * Math.max(1, nextIndex.get()));
    try {
      return completion.get(timeoutSec, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.warn("[Session: {}] Streaming TTS chunk emitter did not finish cleanly: {}",
          sessionId, e.getMessage());
      completion.cancel(true);
      int emitted = emittedChunks.get();
      if (emitted > 0) {
        messageSender.sendAudioComplete(session);
      }
      return emitted;
    }
  }

  private int drainChunks() {
    int index = 0;
    try {
      while (true) {
        CompletableFuture<byte[]> future = waitForFuture(index);
        if (future == null) {
          int emitted = emittedChunks.get();
          if (emitted > 0) {
            messageSender.sendAudioComplete(session);
          }
          return emitted;
        }

        try {
          byte[] pcm = future.get(ttsTimeoutSec, TimeUnit.SECONDS);
          if (pcm != null && pcm.length > 0 && session.isOpen()) {
            messageSender.sendAudioChunk(session, WavEncoder.pcmToWav(pcm), index, false);
            emittedChunks.incrementAndGet();
          }
        } catch (Exception e) {
          future.cancel(true);
          log.warn("[Session: {}] Streaming TTS chunk {} failed", sessionId, index, e);
        } finally {
          futures.remove(index);
          index++;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("[Session: {}] Streaming TTS chunk emitter interrupted", sessionId);
      int emitted = emittedChunks.get();
      if (emitted > 0) {
        messageSender.sendAudioComplete(session);
      }
      return emitted;
    }
  }

  private CompletableFuture<byte[]> waitForFuture(int index) throws InterruptedException {
    synchronized (lock) {
      while (!futures.containsKey(index)) {
        if (totalChunks >= 0 && index >= totalChunks) {
          return null;
        }
        lock.wait(100);
      }
      return futures.get(index);
    }
  }
}
