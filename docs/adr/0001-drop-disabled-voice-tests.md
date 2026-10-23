# 0001 删掉整体 @Disabled 的三个语音面试测试类

- 日期：2026-10-24
- 状态：已采纳

## 背景

`app/src/test/java/ai/interviewer/modules/voiceinterview/service/` 下有三个测试类，类上全部标了 `@Disabled`：

| 文件 | 行数 | 情况 |
| --- | --- | --- |
| `VoiceInterviewServiceTest` | 866 | 约 40 个用例，`setUp` 没注入 `LlmProviderRegistry`，`createSession` 直接 NPE |
| `VoiceInterviewPromptServiceTest` | 24 | 唯一用例 `placeholder()` 是空的，依赖的 `RolePrompt` 已在 `e6bebe7` 删除 |
| `VoiceInterviewServicePauseTest` | 22 | 同上，构造签名从 4 个参数变成十几个 |

三个类的注释都写着 "Pending rewrite … 留作下一步独立 PR 修复"。

## 决策

删除，不保留骨架。

## 为什么

1. **它们在 CI 里一天都没跑过。** JUnit 5 会跳过 `@Disabled`，所以 `./gradlew :app:test` 的用例数（删掉之后实测 505 个）里从来不包含这 866 行。但仓库的测试文件数、行数统计把它们算进去了——数字比真实覆盖好看。
2. **"留作下一步修复"是个没有截止日期的承诺。** `VoiceInterviewPromptServiceTest` 从 `e6bebe7` 起就挂着，到现在至少两周。留一个空 `placeholder()` 不会让它更容易被修。
3. **最坏的影响是误导。** 语音面试是 README 里最显眼的模块，看到 866 行 service 测试会直接得出"这块测过"的结论。实际情况是这块没有 service 层单测。

## 我放弃了什么

- **放弃了 40 个用例里可能仍有价值的断言**，特别是阶段转换的边界 case（什么时候该从 SELF_INTRO 进 TECHNICAL）。删完之后如果要重写，这些 case 得重新想一遍，得重新读一遍 `VoiceInterviewService` 的阶段判断分支。
- **放弃了一个方便的重写起点**：原来只要补一个 mock 就能让 `VoiceInterviewServiceTest` 里大部分 case 跑起来。
- **测试类从 74 个降到 71 个、JUnit 套件从 117 个降到 114 个，测试行数下降约 900 行**，这个变化会在覆盖率报表里体现出来。

## 后果与触发条件

语音面试模块现在只剩 `VoiceInterviewWebSocketHandlerTest`、`VoiceInterviewEvaluationServiceTest`、`VoiceInterviewEvaluationRecoveryTest` 等外围测试，`VoiceInterviewService` 本身没有单测。

重写触发条件（写在这里，免得又变成一个无限期承诺）：

> 下一次修改 `VoiceInterviewService` 的构造签名、或者改动 `createSession` / `pauseSession` 的分支逻辑时，先补对应用例再改代码。如果到 2026-11-30 还没有再动这个类，就不补，承认它是没测的。

## 备注

`VoiceInterviewWebSocketHandlerTest` 里用反射改内部类 `SessionState` 的 `AtomicBoolean` 那个写法保留不动——它不是失效测试，只是难写。难写的原因（handler 当时 1463 行、内部状态不可注入）是后面拆分类时要解决的事；写这条时它已经降到 1398 行，其中 WAV 头拼装先单独抽了出去。
