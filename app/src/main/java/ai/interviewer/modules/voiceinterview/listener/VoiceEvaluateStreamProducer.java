package ai.interviewer.modules.voiceinterview.listener;

import ai.interviewer.common.async.AbstractStreamProducer;
import ai.interviewer.common.constant.AsyncTaskStreamConstants;
import ai.interviewer.common.model.AsyncTaskStatus;
import ai.interviewer.common.transaction.TransactionalExecutor;
import ai.interviewer.infrastructure.redis.RedisService;
import ai.interviewer.modules.voiceinterview.service.VoiceInterviewService;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * 语音面试评估任务生产者
 */
@Slf4j
@Component
public class VoiceEvaluateStreamProducer extends AbstractStreamProducer<String> {

    private final VoiceInterviewService voiceInterviewService;
    private final TransactionalExecutor transactionalExecutor;

    public VoiceEvaluateStreamProducer(RedisService redisService,
                                       @Lazy VoiceInterviewService voiceInterviewService,
                                       TransactionalExecutor transactionalExecutor) {
        super(redisService);
        this.voiceInterviewService = voiceInterviewService;
        this.transactionalExecutor = transactionalExecutor;
    }

    public void sendEvaluateTask(String sessionId) {
        sendTask(sessionId);
    }

    @Override
    protected String taskDisplayName() {
        return "语音面试评估";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.VOICE_EVALUATE_STREAM_KEY;
    }

    @Override
    protected Map<String, String> buildMessage(String sessionId) {
        return Map.of(
            AsyncTaskStreamConstants.FIELD_VOICE_SESSION_ID, sessionId,
            AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"
        );
    }

    @Override
    protected String payloadIdentifier(String sessionId) {
        return "voiceSessionId=" + sessionId;
    }

    @Override
    protected void onSendFailed(String sessionId, String error) {
        transactionalExecutor.runRequiresNew(() -> voiceInterviewService.updateEvaluateStatus(
            Long.parseLong(sessionId), AsyncTaskStatus.FAILED, truncateError(error)));
    }
}
