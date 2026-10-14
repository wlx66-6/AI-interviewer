package ai.interviewer.modules.voiceinterview.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceInterviewMessageDTO {
    private Long id;
    private Long sessionId;
    private String messageType;
    private String phase;
    private String userRecognizedText;
    private String aiGeneratedText;
    private LocalDateTime timestamp;
    private Integer sequenceNum;
}
