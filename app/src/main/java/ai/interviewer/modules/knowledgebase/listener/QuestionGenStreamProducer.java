package ai.interviewer.modules.knowledgebase.listener;

import ai.interviewer.common.async.AbstractStreamProducer;
import ai.interviewer.common.constant.AsyncTaskStreamConstants;
import ai.interviewer.infrastructure.redis.RedisService;
import ai.interviewer.modules.knowledgebase.service.QuestionGenerationStateService;

import org.springframework.stereotype.Component;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * 知识库题目生成任务生产者。
 */
@Slf4j
@Component
public class QuestionGenStreamProducer
    extends AbstractStreamProducer<QuestionGenStreamProducer.QuestionGenTaskPayload> {

  private final QuestionGenerationStateService stateService;

  public record QuestionGenTaskPayload(Long kbId, String taskId, int retryCount) {
  }

  public QuestionGenStreamProducer(
      RedisService redisService,
      QuestionGenerationStateService stateService
  ) {
    super(redisService);
    this.stateService = stateService;
  }

  public boolean sendGenerateTask(Long kbId, String taskId) {
    return sendGenerateTask(kbId, taskId, 0);
  }

  public boolean sendGenerateTask(Long kbId, String taskId, int retryCount) {
    return sendTask(new QuestionGenTaskPayload(kbId, taskId, retryCount));
  }

  @Override
  protected String taskDisplayName() {
    return "题目生成";
  }

  @Override
  protected String streamKey() {
    return AsyncTaskStreamConstants.KB_QUESTION_GEN_STREAM_KEY;
  }

  @Override
  protected Map<String, String> buildMessage(QuestionGenTaskPayload payload) {
    return Map.of(
        AsyncTaskStreamConstants.FIELD_KB_ID, payload.kbId().toString(),
        AsyncTaskStreamConstants.FIELD_TASK_ID, payload.taskId(),
        AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(payload.retryCount())
    );
  }

  @Override
  protected String payloadIdentifier(QuestionGenTaskPayload payload) {
    return "kbId=" + payload.kbId() + ", taskId=" + payload.taskId();
  }

  @Override
  protected void onSendFailed(QuestionGenTaskPayload payload, String error) {
    stateService.markFailed(payload.kbId(), payload.taskId());
  }
}
