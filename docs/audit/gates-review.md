# 五道门禁，曾经一道都没关

2026-10 整理这个仓库时记下来的一份清单。写在这里是因为「以后再犯」的概率不低，
每一条都留了当时的证据和现在的状态。

## 1. CI 从来没跑过

`.github/workflows/ci.yml` 的触发条件写的是 `branches: [master]`，而仓库分支叫 `main`。
也就是说这个 workflow 从创建那天起一次都没触发过，但它的存在让所有人都以为「有 CI」。

已修：`4c0b844`。顺手把 `spotlessCheck`、前端 `lint`、`format:check` 加进了 job。

## 2. commit message 校验钩子没生效

仓库里有一个 `.githooks/commit-msg`，规定标题必须是 `feat|fix|refactor|...` 加中文描述，
正文每行以 `- ` 开头且至少一条。规则本身没错，问题在于 git 不会自动读 `.githooks`，
必须 `git config core.hooksPath .githooks` 一次。这一步没人做过，所以它一次没拦过东西。

已修：README 的克隆步骤里补了这条命令；钩子本身验证过能拦住 `git commit -m "update stuff"`
和没有正文的 `fix: ...` 两种输入。

## 3. ESLint 把要紧的规则降成了警告

`frontend/eslint.config.js` 里 `no-explicit-any` 和四条 `react-hooks` 规则是 `warn`，
而 CI 里没有 lint 这一步——两者叠加的结果是「规则写了，但永远不会失败」。

现状：CI 现在跑 lint，规则级别没动。还剩 62 条 warning 没清，主要是依赖数组和 `any`。
把规则改成 `error` 需要先把这 62 条处理掉，否则每天都是红的。这是欠的账。

## 4. 测试统计里混着一批不会跑的用例

三个测试类整体 `@Disabled`：`VoiceInterviewServiceTest`（866 行，约 40 个用例）、
`VoiceInterviewPromptServiceTest`、`VoiceInterviewServicePauseTest`。注释写着「Pending rewrite」，
没有截止日期。它们的用例数一直算在 README 的 539 里。

已修：`2479edc` 直接删掉，理由写进 [ADR-0001](../adr/0001-drop-disabled-voice-tests.md)。
删完实测 505 个用例，现在 560 个。

E2E 那五个 spec 全部 mock 了后端响应，`knowledgebase-upload.spec.ts` 里手写 20 个 route，
全项目 `waitForResponse` 出现 0 次。它们验证的是前端 mock 的互操作，不是系统行为。
现状：保留，但没有再往里加；`resume-upload.spec.ts` 里有一个用 gate Promise 断言并发上传上限的用例是真的。

## 5. 自己写的规则，自己也违反

`AGENTS.md` 有 203 行硬性规则。抽查下来违反的有：

- 字段注入 `@Autowired` 9 处（规则要求构造器注入）
- `modules/` 下 4 处 `@Value`（规则要求集中到 `@ConfigurationProperties`）

没有违反的（查过，写在这里是为了别再自我怀疑）：`throw new RuntimeException` 0 处、
`printStackTrace` 0 处、Controller 返回 Entity 0 处、TODO/FIXME 0 处。

`Executors.newVirtualThreadPerTaskExecutor()` 有 2 处，看着像违规，但 Java 25 下这是正确写法，
规则本意是禁止无界线程池，这条不算。

现状：未修。9 处 `@Autowired` 分散在几个低频 Service 里，改构造器注入要动它们的测试，
收益是规则一致而不是功能正确，排在上传校验和语音链路后面。

## 结论

五条里四条是「装了门框、没装门扇」：文件在、规则在、流程在，但没有一个能在写错的时候让人疼一下。
真正的问题不是不会写，是没有验收习惯——写完就提交，等 CI 绿，而 CI 根本没跑。
