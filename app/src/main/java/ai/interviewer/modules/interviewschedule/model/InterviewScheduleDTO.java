package ai.interviewer.modules.interviewschedule.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class InterviewScheduleDTO {
    private Long id;
    private String companyName;
    private String position;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private java.time.LocalDateTime interviewTime;
    private String interviewType;
    private String meetingLink;
    private Integer roundNumber;
    private String interviewer;
    private String notes;
    private InterviewStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
