package ai.interviewer.modules.interviewschedule.service;

import ai.interviewer.modules.interviewschedule.model.InterviewStatus;
import ai.interviewer.modules.interviewschedule.repository.InterviewScheduleRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleStatusUpdater {

    private final InterviewScheduleRepository repository;

    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void updateExpiredInterviews() {
        int updated = repository.updateStatusByStatusAndInterviewTimeBefore(
            InterviewStatus.CANCELLED, InterviewStatus.PENDING, LocalDateTime.now());

        if (updated > 0) {
            log.info("已将 {} 条过期面试标记为已取消", updated);
        }
    }
}
