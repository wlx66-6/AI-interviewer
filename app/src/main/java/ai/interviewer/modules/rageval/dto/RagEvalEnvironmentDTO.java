package ai.interviewer.modules.rageval.dto;

import java.util.List;

/**
 * 一次评测运行的环境快照，是两次运行可比的前提。
 *
 * <p>归档报告里这些值多为「值 + 说明」的字符串（例如 {@code "false(rag-eval Profile 默认)"}、
 * {@code "800(默认)"}），因此解析时只提取可判断的部分为布尔/数值，原文一并保留在
 * {@code resourceFingerprints} 与备注字段里，避免把「说明文字」当成配置值。
 *
 * @param resourceFingerprints 语料、Prompt 模板与分块数的指纹清单，元素形如
 *     {@code fixtureSha256:corpus-java-backend.md=081a5d118866a258}
 * @param tokenUsageNote 归档报告对 Token 用量的说明（早期报告无法从 {@code .content()} 取到 usage）
 */
public record RagEvalEnvironmentDTO(
    String model,
    String embeddingModel,
    Double temperature,
    String defaultChatProvider,
    String defaultEmbeddingProvider,
    Integer chunkSize,
    Boolean rewriteEnabled,
    Boolean mergeOriginalQuery,
    String springAiVersion,
    String datasetSha256,
    String gitSha,
    List<String> resourceFingerprints,
    String tokenUsageNote) {}
