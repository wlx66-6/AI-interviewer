package ai.interviewer.modules.tokenusage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单次 LLM 调用的 Token 用量记录。
 *
 * <p>{@code kind} 区分 Chat 与 Embedding；{@code source} 记录调用来源的 ChatClient 类型
 * （default / plain / voice / embedding），便于按场景归因用量。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "llm_token_usage")
public class LlmTokenUsageEntity {

  /** 调用类别。 */
  public static final String KIND_CHAT = "CHAT";
  public static final String KIND_EMBEDDING = "EMBEDDING";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "provider_id", nullable = false, length = 64)
  private String providerId;

  @Column(length = 128)
  private String model;

  @Column(nullable = false, length = 16)
  private String kind;

  @Column(nullable = false, length = 32)
  private String source;

  @Column(name = "prompt_tokens", nullable = false)
  private int promptTokens;

  @Column(name = "completion_tokens", nullable = false)
  private int completionTokens;

  @Column(name = "total_tokens", nullable = false)
  private int totalTokens;

  @Column(name = "latency_ms")
  private Long latencyMs;

  @Column(nullable = false)
  private boolean success;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
