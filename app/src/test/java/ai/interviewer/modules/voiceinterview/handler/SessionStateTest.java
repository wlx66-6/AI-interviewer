package ai.interviewer.modules.voiceinterview.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("语音面试连接状态")
class SessionStateTest {

  @Nested
  @DisplayName("多段 STT 定稿拼接")
  class MergeSegments {

    @Test
    @DisplayName("第二段是前一段的超集时，只保留更完整的句子")
    void supersetReplacesPrevious() {
      SessionState state = new SessionState();

      state.appendFinalSttSegment("我想做一个");
      state.appendFinalSttSegment("我想做一个秒杀系统");

      assertThat(state.getMergeBufferPreview()).isEqualTo("我想做一个秒杀系统");
    }

    @Test
    @DisplayName("重复的定稿不会拼两遍")
    void identicalSegmentIsNotDuplicated() {
      SessionState state = new SessionState();

      state.appendFinalSttSegment("介绍一下线程池");
      state.appendFinalSttSegment("介绍一下线程池");

      assertThat(state.getMergeBufferPreview()).isEqualTo("介绍一下线程池");
    }

    @Test
    @DisplayName("前一段已经包含后一段时不追加")
    void shorterTrailingSegmentIsDropped() {
      SessionState state = new SessionState();

      state.appendFinalSttSegment("那你说说垃圾回收");
      state.appendFinalSttSegment("垃圾回收");

      assertThat(state.getMergeBufferPreview()).isEqualTo("那你说说垃圾回收");
    }

    @Test
    @DisplayName("两段互不包含时用中文逗号接起来，句子以句号结尾时用空格")
    void independentSegmentsAreJoined() {
      SessionState state = new SessionState();
      state.appendFinalSttSegment("我叫张三");
      state.appendFinalSttSegment("今年毕业");
      assertThat(state.getMergeBufferPreview()).isEqualTo("我叫张三，今年毕业");

      SessionState ended = new SessionState();
      ended.appendFinalSttSegment("第一题答完了。");
      ended.appendFinalSttSegment("第二题开始");
      assertThat(ended.getMergeBufferPreview()).isEqualTo("第一题答完了。 第二题开始");
    }

    @Test
    @DisplayName("空定稿不会改变缓冲区，也不会启动计时")
    void blankSegmentIsIgnored() {
      SessionState state = new SessionState();

      state.appendFinalSttSegment("   ");

      assertThat(state.getMergeBufferPreview()).isEmpty();
      assertThat(state.takeMergeBufferAndClear()).isEmpty();
    }
  }

  @Nested
  @DisplayName("取走缓冲区")
  class TakeBuffer {

    @Test
    @DisplayName("取走后缓冲区清空，下次拼接收到的第一段重新开始")
    void takeClearsBuffer() {
      SessionState state = new SessionState();
      state.appendFinalSttSegment("第一段");

      assertThat(state.takeMergeBufferAndClear()).isEqualTo("第一段");
      assertThat(state.getMergeBufferPreview()).isEmpty();

      state.appendFinalSttSegment("第二段");
      assertThat(state.getMergeBufferPreview()).isEqualTo("第二段");
    }

    @Test
    @DisplayName("未定稿的 partial 只用于预览，不进缓冲区")
    void partialOnlyAffectsPreview() {
      SessionState state = new SessionState();
      state.appendFinalSttSegment("我想问");

      assertThat(state.getMergeBufferPreviewWithPartial("我想问一下并发")).isEqualTo("我想问一下并发");
      assertThat(state.getMergeBufferPreview()).isEqualTo("我想问");
    }
  }

  @Nested
  @DisplayName("回声抑制")
  class AiSpeaking {

    @Test
    @DisplayName("AI 说话期间判定为不可接收用户音频")
    void busyWhileSpeaking() {
      SessionState state = new SessionState();
      state.markAiSpeaking();

      assertThat(state.isAiSpeakingOrCooldown()).isTrue();
    }

    @Test
    @DisplayName("AI 说完后留一小段冷却期，期间仍然不收音频")
    void cooldownAfterSpeaking() {
      SessionState state = new SessionState();
      state.markAiSpeaking();
      state.markAiSpeakingFinished(200);

      assertThat(state.isAiSpeakingOrCooldown()).isTrue();
    }

    @Test
    @DisplayName("冷却期过了就恢复正常")
    void cooldownExpires() throws Exception {
      SessionState state = new SessionState();
      state.markAiSpeakingFinished(10);

      Thread.sleep(30);

      assertThat(state.isAiSpeakingOrCooldown()).isFalse();
    }
  }
}
