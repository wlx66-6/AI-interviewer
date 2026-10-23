package ai.interviewer.modules.voiceinterview.repository;

import ai.interviewer.modules.voiceinterview.model.VoiceInterviewMessageEntity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话消息计数批量查询的 H2 集成测试。
 *
 * <p>分组的 JPQL 在 Mockito 单测里验证不了，只能在真实数据库上跑。这里确认三件事：
 * SUMMARY 行不计入、一次查询覆盖多个会话、只有摘要行的会话不出现在结果里（调用方按 0 处理）。
 */
@SpringBootTest(classes = VoiceInterviewMessageRepositoryTest.JpaOnlyConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("会话消息计数批量查询")
class VoiceInterviewMessageRepositoryTest {

    @EnableAutoConfiguration(
        exclude = {RedissonAutoConfigurationV2.class, RedissonAutoConfigurationV4.class})
    @EnableJpaRepositories(basePackageClasses = VoiceInterviewMessageRepository.class)
    @EntityScan(basePackageClasses = VoiceInterviewMessageEntity.class)
    static class JpaOnlyConfig {
    }

    private static final LocalDateTime MESSAGE_TIME = LocalDateTime.of(2026, 10, 24, 0, 0);

    @Autowired
    private VoiceInterviewMessageRepository repository;

    @Test
    @DisplayName("SUMMARY 行不计入对话条数")
    void excludesSummaryRows() {
        repository.save(message(1L, "USER_SPEECH", 1));
        repository.save(message(1L, "AI_SPEECH", 2));
        repository.save(message(1L, VoiceInterviewMessageEntity.MESSAGE_TYPE_SUMMARY, 3));

        assertThat(countsOf(1L)).containsEntry(1L, 2L);
    }

    @Test
    @DisplayName("一次查询覆盖多个会话")
    void countsSeveralSessionsAtOnce() {
        repository.save(message(1L, "USER_SPEECH", 1));
        repository.save(message(2L, "USER_SPEECH", 1));
        repository.save(message(2L, "AI_SPEECH", 2));

        assertThat(countsOf(1L, 2L)).containsOnly(Map.entry(1L, 1L), Map.entry(2L, 2L));
    }

    @Test
    @DisplayName("只有摘要行的会话不返回计数，由调用方按 0 处理")
    void skipsSessionsWithoutDialogue() {
        repository.save(message(7L, VoiceInterviewMessageEntity.MESSAGE_TYPE_SUMMARY, 1));

        assertThat(countsOf(7L)).doesNotContainKey(7L);
    }

    private Map<Long, Long> countsOf(Long... sessionIds) {
        return repository
            .countBySessionIds(List.of(sessionIds), VoiceInterviewMessageEntity.MESSAGE_TYPE_SUMMARY)
            .stream()
            .collect(Collectors.toMap(
                VoiceInterviewMessageRepository.SessionMessageCount::getSessionId,
                VoiceInterviewMessageRepository.SessionMessageCount::getCount));
    }

    private static VoiceInterviewMessageEntity message(Long sessionId, String messageType, int sequenceNum) {
        return VoiceInterviewMessageEntity.builder()
            .sessionId(sessionId)
            .messageType(messageType)
            .sequenceNum(sequenceNum)
            .timestamp(MESSAGE_TIME)
            .createdAt(MESSAGE_TIME)
            .build();
    }
}
