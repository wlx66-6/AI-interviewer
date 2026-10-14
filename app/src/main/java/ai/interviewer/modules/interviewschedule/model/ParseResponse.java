package ai.interviewer.modules.interviewschedule.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParseResponse {
    private Boolean success;
    private CreateInterviewRequest data;
    private Double confidence;
    private String parseMethod; // rule, ai
    private String log;
}
