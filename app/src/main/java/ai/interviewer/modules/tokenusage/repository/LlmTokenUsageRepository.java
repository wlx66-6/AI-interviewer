package ai.interviewer.modules.tokenusage.repository;

import ai.interviewer.modules.tokenusage.dto.TokenUsageAggregate;
import ai.interviewer.modules.tokenusage.model.LlmTokenUsageEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** LLM Token 用量记录的只读聚合查询。 */
@Repository
public interface LlmTokenUsageRepository extends JpaRepository<LlmTokenUsageEntity, Long> {

  /**
   * 按「提供方 / 模型 / 类别 / 来源」四维聚合指定时间窗内的用量。
   *
   * <p>聚合在数据库内完成，避免把明细行取回内存再统计。
   */
  @Query(
      """
      select new ai.interviewer.modules.tokenusage.dto.TokenUsageAggregate(
          u.providerId, u.model, u.kind, u.source,
          sum(u.promptTokens), sum(u.completionTokens), sum(u.totalTokens),
          count(u), avg(u.latencyMs),
          sum(case when u.success = false then 1 else 0 end))
      from LlmTokenUsageEntity u
      where u.createdAt >= :from and u.createdAt < :to
      group by u.providerId, u.model, u.kind, u.source
      order by sum(u.totalTokens) desc
      """)
  List<TokenUsageAggregate> aggregate(
      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
