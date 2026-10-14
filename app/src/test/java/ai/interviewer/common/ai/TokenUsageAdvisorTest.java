package ai.interviewer.common.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import reactor.core.publisher.Flux;

/**
 * {@link TokenUsageAdvisor} 的单元测试。
 *
 * <p>不访问真实 LLM：直接构造 {@code ChatClientResponse} 并伪造链，验证「拦截与上报」行为本身。
 */
class TokenUsageAdvisorTest {

  private static final String PROVIDER = "dashscope";

  private final List<LlmUsageSink.LlmUsage> recorded = new ArrayList<>();

  private TokenUsageAdvisor advisor(LlmUsageSink sink) {
    return new TokenUsageAdvisor(PROVIDER, LlmUsageSink.SOURCE_DEFAULT, sink);
  }

  @Nested
  @DisplayName("同步调用 adviseCall")
  class AdviseCall {

    @Test
    @DisplayName("响应带 usage 时上报输入/输出/总 Token 与模型名")
    void recordsUsage() {
      ChatClientResponse response = responseWithUsage("qwen-plus", 120, 80);

      ChatClientResponse returned =
          advisor(recorded::add)
              .adviseCall(request(), callChainReturning(response));

      assertThat(returned).isSameAs(response);
      assertThat(recorded).hasSize(1);
      LlmUsageSink.LlmUsage usage = recorded.get(0);
      assertThat(usage.providerId()).isEqualTo(PROVIDER);
      assertThat(usage.model()).isEqualTo("qwen-plus");
      assertThat(usage.kind()).isEqualTo(LlmUsageSink.KIND_CHAT);
      assertThat(usage.source()).isEqualTo(LlmUsageSink.SOURCE_DEFAULT);
      assertThat(usage.promptTokens()).isEqualTo(120);
      assertThat(usage.completionTokens()).isEqualTo(80);
      assertThat(usage.totalTokens()).isEqualTo(200);
      assertThat(usage.success()).isTrue();
    }

    @Test
    @DisplayName("usage 为空 usage 对象时不记录，避免写入 0 值污染统计")
    void skipsEmptyUsage() {
      ChatClientResponse response = responseWithUsageObject(new EmptyUsage());

      advisor(recorded::add).adviseCall(request(), callChainReturning(response));

      assertThat(recorded).isEmpty();
    }

    @Test
    @DisplayName("元数据缺失时不记录，也不抛异常")
    void skipsMissingMetadata() {
      ChatClientResponse response = new ChatClientResponse(null, Map.of());

      ChatClientResponse returned =
          advisor(recorded::add).adviseCall(request(), callChainReturning(response));

      assertThat(returned).isSameAs(response);
      assertThat(recorded).isEmpty();
    }

    @Test
    @DisplayName("调用抛异常时记录一条失败并原样抛出")
    void recordsFailureAndRethrows() {
      RuntimeException boom = new IllegalStateException("upstream down");
      CallAdvisorChain chain =
          new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest request) {
              throw boom;
            }

            @Override
            public List<CallAdvisor> getCallAdvisors() {
              return List.of();
            }

            @Override
            public CallAdvisorChain copy(CallAdvisor advisor) {
              return this;
            }
          };

      assertThatThrownBy(() -> advisor(recorded::add).adviseCall(request(), chain))
          .isSameAs(boom);

      assertThat(recorded).hasSize(1);
      assertThat(recorded.get(0).success()).isFalse();
      assertThat(recorded.get(0).totalTokens()).isZero();
    }
  }

  @Nested
  @DisplayName("流式调用 adviseStream")
  class AdviseStream {

    @Test
    @DisplayName("多个分片带 usage 时只取最后一块上报一次")
    void recordsLastUsageOnce() {
      Flux<ChatClientResponse> flux =
          Flux.just(
              responseWithUsage("qwen-plus", 100, 0),
              responseWithUsage("qwen-plus", 100, 10),
              responseWithUsage("qwen-plus", 100, 40));

      advisor(recorded::add)
          .adviseStream(request(), streamChain(flux))
          .collectList()
          .block();

      assertThat(recorded).hasSize(1);
      assertThat(recorded.get(0).completionTokens()).isEqualTo(40);
      assertThat(recorded.get(0).totalTokens()).isEqualTo(140);
    }

    @Test
    @DisplayName("全程没有 usage 时不记录")
    void skipsWhenNoUsage() {
      Flux<ChatClientResponse> flux =
          Flux.just(responseWithUsageObject(new EmptyUsage()), responseWithUsageObject(new EmptyUsage()));

      advisor(recorded::add)
          .adviseStream(request(), streamChain(flux))
          .collectList()
          .block();

      assertThat(recorded).isEmpty();
    }

    @Test
    @DisplayName("流中出现错误时记录一条失败，且错误照常向下游传播")
    void recordsFailureAndPropagates() {
      Flux<ChatClientResponse> flux = Flux.error(new IllegalStateException("stream broken"));

      assertThatThrownBy(
              () ->
                  advisor(recorded::add)
                      .adviseStream(request(), streamChain(flux))
                      .collectList()
                      .block())
          .hasMessageContaining("stream broken");

      assertThat(recorded).hasSize(1);
      assertThat(recorded.get(0).success()).isFalse();
    }
  }

  @Nested
  @DisplayName("旁路容错")
  class Resilience {

    @Test
    @DisplayName("上报端抛异常时被吞掉，主链路返回值不受影响")
    void sinkFailureDoesNotBreakCall() {
      LlmUsageSink brokenSink =
          usage -> {
            throw new IllegalStateException("db down");
          };
      ChatClientResponse response = responseWithUsage("qwen-plus", 10, 10);

      ChatClientResponse returned =
          advisor(brokenSink).adviseCall(request(), callChainReturning(response));

      assertThat(returned).isSameAs(response);
    }
  }

  @Test
  @DisplayName("默认顺序为 0（最外层），先于安全护栏执行")
  void defaultOrder() {
    assertThat(advisor(recorded::add).getOrder()).isZero();
    assertThat(advisor(recorded::add).getName()).isEqualTo("TokenUsageAdvisor");
  }

  private static ChatClientRequest request() {
    return new ChatClientRequest(new Prompt("你好"), Map.of());
  }

  private static ChatClientResponse responseWithUsage(
      String model, Integer promptTokens, Integer completionTokens) {
    return responseWithUsageObject(new DefaultUsage(promptTokens, completionTokens), model);
  }

  private static ChatClientResponse responseWithUsageObject(Usage usage) {
    return responseWithUsageObject(usage, "qwen-plus");
  }

  private static ChatClientResponse responseWithUsageObject(Usage usage, String model) {
    ChatResponseMetadata metadata =
        ChatResponseMetadata.builder().model(model).usage(usage).build();
    return new ChatClientResponse(
        ChatResponse.builder().generations(List.of()).metadata(metadata).build(), Map.of());
  }

  private static CallAdvisorChain callChainReturning(ChatClientResponse response) {
    return new CallAdvisorChain() {
      @Override
      public ChatClientResponse nextCall(ChatClientRequest request) {
        return response;
      }

      @Override
      public List<CallAdvisor> getCallAdvisors() {
        return List.of();
      }

      @Override
      public CallAdvisorChain copy(CallAdvisor advisor) {
        return this;
      }
    };
  }

  private static StreamAdvisorChain streamChain(Flux<ChatClientResponse> flux) {
    return new StreamAdvisorChain() {
      @Override
      public Flux<ChatClientResponse> nextStream(ChatClientRequest request) {
        return flux;
      }

      @Override
      public List<StreamAdvisor> getStreamAdvisors() {
        return List.of();
      }

      @Override
      public StreamAdvisorChain copy(StreamAdvisor advisor) {
        return this;
      }
    };
  }
}
