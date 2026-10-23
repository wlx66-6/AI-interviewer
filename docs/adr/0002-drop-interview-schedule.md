# 0002 删除面试日程模块

## 背景

`/api/interview-schedule` 是一套完整的面试日程管理功能，从 V1 建库起就存在：

| 层 | 内容 |
| --- | --- |
| 后端 | `modules/interviewschedule/` 11 个文件：CRUD 控制器、`InterviewScheduleEntity`（表 `interview_schedule`）、Repository、`InterviewScheduleService`、`InterviewParseService`（正则规则 + LLM 双路解析面试邀约文本）、`ScheduleStatusUpdater`（`@Scheduled(cron = "0 0 * * * ?")` 每小时把过期日程推成 `COMPLETED`）、6 个 DTO/枚举 |
| 前端 | 11 个文件：`InterviewSchedulePage`、日历/列表/表单三个视图组件、`useInterviewSchedule`、api 客户端、类型定义、导航项、路由 |
| 数据库 | `interview_schedule` 表，状态机 `PENDING / COMPLETED / CANCELLED / RESCHEDULED` |

它不算坏代码：解析服务把规则解析和 AI 解析串起来，状态刷新走 `@Scheduled` + 批量 `update`。

## 决策

整块删除，包括数据库表（`V20261025__drop_interview_schedule_table.sql`）。

## 为什么删

- **没有任何测试。** 全仓 535 个用例里，这个模块占 0 个。
- **没有任何文档提过它。** README、`docs/`、`AGENTS.md` 全文搜「日程」「interview-schedule」「interview_schedule」零命中。一个功能上线了却没有出现在任何文档里，通常意味着作者自己也没把它算进产品。
- **和主线弱相关。** 这个项目的题目是「AI 面试官」：简历解析 → 题库检索 → 文字/语音面试 → 评估报告。面试日程是「记下明天几点有面试、到点提醒」，它不参与面试本身，删掉之后主线一句解释都不用改。
- 它自带一套独立状态机、一个独立定时任务、一个独立 LLM 解析入口，这三样都只服务它自己。留着意味着以后每次动公共能力（认证、限流、日志、Result 包装）都要在脑子里过一遍它。

## 我放弃了什么

- **一个真实可用的功能**：把招聘方发来的面试邀约文本粘进去，自动解析出公司、岗位、时间，落到日历上。这对正在找工作的人是有用的。
- **规则 + LLM 双路解析的实现**：正则先抽明显字段，抽不全再交给 `ChatClient` 补，是一份不错的「能用规则解决的别交给模型」样例。
- **一个 `@Scheduled` 批量状态推进的写法**：`updateStatusByStatusAndInterviewTimeBefore` 这类整批 update 在项目里只此一处。
- 前端一套日历视图、一个列表/日历双视图切换的交互。

代价是项目少了一个能演示的功能：22 个文件、1 张表。

## 考虑过但没选的方案

- **保留代码，标成 experimental。** 项目里已经有过一次教训：`@Disabled` 的测试类留在仓库里，统计上算测试、实际上一天没跑过。功能也一样，标了实验性但没人管，最后只会变成"这个项目声称支持日程管理"。
- **只删前端，保留后端接口。** 表还在、迁移还在、每次改公共能力还要编过它，收益只有"接口还在"。

## 如果以后要恢复

`git log --diff-filter=D -- path/to/interviewschedule` 能找回全部代码，恢复成本大约一天（主要是把 `InterviewStatus` 接到现在的限流和日志规范上）。真要恢复的话，前提是先想清楚它和「AI 面试」的关系，而不是把它当成第二个 TODO 列表。
