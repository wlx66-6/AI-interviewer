package ai.interviewer.modules.voiceinterview.handler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一次语音面试连接的可变状态。
 *
 * 从 WebSocketHandler 的内部类挪出来，是因为里面「多段 STT 定稿怎么拼」的逻辑是纯字符串处理，
 * 之前没有任何测试能碰到它；现在同包的测试可以直接构造它。
 */
class SessionState {

    private final AtomicReference<String> accumulatedText = new AtomicReference<>("");
    private final AtomicBoolean processing = new AtomicBoolean(false);
    /** AI 正在播放 TTS 音频，期间丢弃麦克风回声 */
    private final AtomicBoolean aiSpeaking = new AtomicBoolean(false);
    /** AI 音频播放结束后，额外等待这段时间再接受用户音频（ms），防止回声尾音 */
    private final AtomicLong aiSpeakEndAt = new AtomicLong(0);
    /** 多段 STT completed 拼接，防抖后再送 LLM */
    private final AtomicReference<String> mergeBuffer = new AtomicReference<>("");
    /** mergeBuffer 开始计时点，用于”最长等待补充”判定 */
    private final AtomicLong mergeStartedAt = new AtomicLong(0);
    /** 最近一次 STT 活动时间（partial/final） */
    private final AtomicLong lastSttActivityAt = new AtomicLong(System.currentTimeMillis());
    /** 当前正在执行 LLM+TTS 管线的虚拟线程，断连时可中断 */
    private volatile Thread processingThread = null;

    void appendFinalSttSegment(String segment) {
        String s = segment == null ? "" : segment.trim();
        if (s.isEmpty()) {
            return;
        }
        mergeBuffer.updateAndGet(prev -> {
            if (prev == null || prev.isEmpty()) {
                mergeStartedAt.set(System.currentTimeMillis());
                return s;
            }
            return joinSegments(prev, s);
        });
        markSttActivity();
    }

    private static String joinSegments(String previous, String next) {
        String trimmedPrevious = previous.trim();
        String trimmedNext = next.trim();
        if (trimmedNext.equals(trimmedPrevious) || trimmedNext.startsWith(trimmedPrevious)) {
            return trimmedNext;
        }
        if (trimmedPrevious.endsWith(trimmedNext)) {
            return trimmedPrevious;
        }
        if (trimmedPrevious.endsWith("。") || trimmedPrevious.endsWith("！")
                || trimmedPrevious.endsWith("？") || trimmedPrevious.endsWith(".")
                || trimmedPrevious.endsWith("!") || trimmedPrevious.endsWith("?")) {
            return trimmedPrevious + " " + trimmedNext;
        }
        return trimmedPrevious + "，" + trimmedNext;
    }

    String getMergeBufferPreview() {
        String s = mergeBuffer.get();
        return s == null ? "" : s;
    }

    void setMergeBufferDirectly(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) {
            return;
        }
        mergeBuffer.set(s);
        if (mergeStartedAt.get() == 0) {
            mergeStartedAt.set(System.currentTimeMillis());
        }
    }

    String getMergeBufferPreviewWithPartial(String partial) {
        String current = partial == null ? "" : partial.trim();
        if (current.isEmpty()) {
            return getMergeBufferPreview();
        }

        String confirmed = getMergeBufferPreview();
        if (confirmed.isBlank()) {
            return current;
        }
        return joinSegments(confirmed, current);
    }

    String takeMergeBufferAndClear() {
        mergeStartedAt.set(0);
        return mergeBuffer.getAndSet("");
    }

    void markSttActivity() {
        lastSttActivityAt.set(System.currentTimeMillis());
    }

    long getMergeStartedAt() {
        long value = mergeStartedAt.get();
        return value > 0 ? value : System.currentTimeMillis();
    }

    long getLastSttActivityAt() {
        return lastSttActivityAt.get();
    }

    String getAccumulatedText() {
        return accumulatedText.get();
    }

    void setAccumulatedText(String text) {
        accumulatedText.set(text);
    }

    AtomicBoolean isProcessing() {
        return processing;
    }

    void setProcessingThread(Thread t) {
        this.processingThread = t;
    }

    Thread getProcessingThread() {
        return processingThread;
    }

    /** AI 开始说话：这期间收到的麦克风音频按回声丢弃 */
    void markAiSpeaking() {
        aiSpeaking.set(true);
    }

    /** AI 说完：解除回声抑制，并留一小段冷却期 */
    void markAiSpeakingFinished(long cooldownMillis) {
        aiSpeaking.set(false);
        aiSpeakEndAt.set(System.currentTimeMillis() + cooldownMillis);
    }

    boolean isAiSpeakingOrCooldown() {
        if (aiSpeaking.get()) {
            return true;
        }
        // AI 播放结束后的冷却期（默认 800ms），防止扬声器尾音被录入
        return System.currentTimeMillis() < aiSpeakEndAt.get();
    }
}
