package ai.interviewer.modules.voiceinterview.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketControlMessage {
    private String type; // "control"
    private String action; // "start_phase", "end_phase", "end_interview", "submit"
    private String phase; // "INTRO", "TECH", "PROJECT", "HR"
    private Map<String, Object> data;
}
