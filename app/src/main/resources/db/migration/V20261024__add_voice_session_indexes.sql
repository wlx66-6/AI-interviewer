-- 语音面试的三张表从 V1 起一直没有索引：会话列表按 user_id + updated_at 取，
-- 恢复任务按 status / evaluate_status + 时间扫，消息回放按 session_id + sequence_num 取，
-- 全都是顺序扫描。数据量小时看不出来，但这几条是页面首屏路径。
CREATE INDEX idx_voice_session_user_updated
    ON voice_interview_sessions (user_id, updated_at);

CREATE INDEX idx_voice_session_user_status_updated
    ON voice_interview_sessions (user_id, status, updated_at);

CREATE INDEX idx_voice_session_status_start
    ON voice_interview_sessions (status, start_time);

CREATE INDEX idx_voice_session_evaluate_updated
    ON voice_interview_sessions (evaluate_status, updated_at);

CREATE INDEX idx_voice_message_session_seq
    ON voice_interview_messages (session_id, sequence_num);

CREATE INDEX idx_voice_message_session_type
    ON voice_interview_messages (session_id, message_type);
