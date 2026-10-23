-- 面试日程功能已删除（见 docs/adr/0002-drop-interview-schedule.md）。
-- 表从 V1 起就存在，只能靠迁移删掉；先不删数据，只把表从 schema 里摘出去。
DROP TABLE IF EXISTS interview_schedule;
