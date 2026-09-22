# Android 16 真机验收协议 — 2026-09-22

适用仓库：`EmilioJI/jev-chat-jarvis`

## 验收包

### A — 兼容基线 / 当前集成候选

- package: `com.jev.probe.guofeng`
- label: `Jev聊天助手·国风验收`
- `isAccessibilityTool=true`
- source commit: `21cbf0108382c9919319535e5a3416c587818548`
- Run #33: `35722055820`
- JVM unit tests: `testDebugUnitTest` PASS
- APK SHA-256:
  `82d130d950182804cb7c2455f7c9c8adc3170f9efabe962ca593a41efc73244d`

### B — Play 合规 A/B 实验候选 v3

- package: `com.jev.probe.compliance`
- label: `Jev聊天助手·合规A/B`
- `isAccessibilityTool=false`
- source commit: `73cd437f73965771cf46478b6413c217db9633dd`
- Run #29: `35721226611`
- APK SHA-256:
  `4b04615fa0f73d8ca55b21233df55e4f36d52e61f516ee704d2e4b716520aa62`

B v3 与 A 使用同一 transport-hardened 运行逻辑基线；A 在 B 构建后仅增加 JVM
测试源码、JUnit test-only 依赖以及将 endpoint validator 从 private 调整为 internal 供同
module 测试调用，不改变 Accessibility / capture / model / OCR 运行行为。除
`isAccessibilityTool` 以及为并行安装而使用的 package / launcher label 外，没有与本次
微信 A/B 判定相关的业务行为差异。

A 与 B 可与官方 `com.jev.probe` 并行安装。

## Gate 0 — 安装与启动

对 A、B 分别验证：

- 安装成功
- 首屏无崩溃
- 国风 UI 正常
- 设置页可滚动
- 知识库页可进入
- “捕获诊断”页可进入

PASS 条件：两包都通过。

## Gate 1 — 权限前醒目披露

点击首页“无障碍权限”。

必须先出现 App 内独立披露，包含：

- 读取当前可见聊天内容 / 控件结构 / 标题
- OCR fallback 可能截屏
- 默认 ML Kit 本地 OCR 不上传
- Vision API 只有显式选择时上传裁剪聊天区域
- 分析文本会发给用户自己选择的模型服务商
- 不读取聊天数据库
- 不自动发送

验证：

- 点“不同意”不会跳系统设置
- 再次点击可重新看到披露
- 点“同意并前往系统设置”才进入系统无障碍设置

PASS 条件：行为完全符合上面流程。

## Gate 2 — AndroidKeyStore 自检

设置 → 自检。

必须看到：

- 知识库 / 历史方向自检通过
- AndroidKeyStore AES-GCM 自检通过
- Vision OCR parser 自检通过

安全自检使用 dummy 值，不读取真实 API Key，不联网。

PASS 条件：三项均 PASS。

## Gate 3 — 微信 Accessibility A/B（关键 Gate）— COMPLETED

设备：
- vivo X100 Ultra
- Android 16
- 主微信 user 0 / `com.tencent.mm`

### A — `isAccessibilityTool=true`

服务状态：
- Enabled = YES
- Bound = YES
- event types = WINDOW_STATE_CHANGED / WINDOW_CONTENT_CHANGED / VIEW_SCROLLED

同一微信聊天诊断：
- package = `com.tencent.mm`
- adapter = `WeChat`
- source = `tree`
- message_count = `1`
- status = `ok`

**A = PASS。**

### B — `isAccessibilityTool=false`

服务状态：
- Enabled = YES
- Bound = YES
- capabilities / event types 正常

控制实验：
- 普通第三方测试 App 可更新 diagnostics，证明服务本身工作正常
- 微信不产生新的捕获 diagnostics
- X 也不产生新的捕获 diagnostics

**B = FAIL。**

该表现与 Android API 34+ `accessibilityDataSensitive` 机制一致：敏感
AccessibilityEvent / View 可限制为仅向 `isAccessibilityTool=true` 的服务暴露。

结论：
- PR #16 不合并，已关闭
- 当前兼容构建继续保留 `isAccessibilityTool=true`
- Google Play 发布不能把当前架构直接改成 false 后继续宣称微信/X 控件树能力不变
- Play-safe 版本需要不依赖该 flag 的捕获路径（例如显式用户授权的 screen capture / OCR 模式）

## Gate 4 — QQ / X / 飞书回归

分别进入已支持聊天：

- QQ
- X DM
- 飞书

诊断页要求：

- 对应适配器正确
- QQ / X 优先控件树
- 飞书允许 `tree_empty → ocr_local`
- 没有反复自动分析旧历史
- 向上滚动不会触发新的模型分析

PASS 条件：与 v1.3 既有能力无回退。

## Gate 5 — 悬浮窗安全

验证：

- A 会话开始分析后立即切到 B 会话
- A 的迟到结果不得出现在 B
- A 的候选回复不得填进 B
- 在候选显示后切换会话再点“填入”，应提示“会话已变化，请重新分析”
- 永远不自动点击“发送”

PASS 条件：无串会话、无错填、无自动发送。

## Gate 6 — GLM-4.7 实网

不要在聊天或 GitHub issue 中粘贴 Key。

设置建议：

判断引擎：
- GLM-4.7
- base `https://open.bigmodel.cn/api/paas/v4`
- model `glm-4.7`

回复接口：
- GLM-4.7
- 同 host 时回复 Key 可留空继承判断 Key

先点：

- 测试判断
- 测试回复

然后在一个非敏感测试会话运行一次完整分析。

PASS 条件：

- judgment 返回 7 个结构化字段
- 3 条候选回复生成并完成排序
- 不出现 JSON/schema 错误
- UI 无卡死
- Key 在重启 App 后仍能正常读取

## Gate 7 — OpenRouter Jev 实网

判断引擎：
- OpenRouter（推荐）
- model `typesafe/jev-1.13`

PASS 条件：

- 连通测试成功
- 正常对话判断成功
- reply route 若不是 OpenRouter，必须单独配置对应 provider Key
- 不发生跨 host Key 继承

## Gate 8 — Vision OCR

### 本地默认

OCR 引擎选择：
- 本地 ML Kit（默认）

验证：
- 不需要 Vision Key
- 飞书/手动 OCR 可识别
- 诊断来源显示 `本地 ML Kit OCR`

### 远程 Vision

只有测试时主动选择：
- 视觉 API（上传裁剪聊天区）

验证：
- 诊断来源显示 `远程 Vision OCR`
- 返回无说话人标签或接口失败时自动回退本地 OCR
- UI 对用户明确提示回退
- 不上传完整状态栏/标题栏/输入区

PASS 条件：本地默认不上传；远程显式选择且 fallback 正常。

## Gate 9 — 历史与滚动摘要

历史默认关闭。

开启历史后：

- 向上滚旧消息不得追加成最新历史
- 长时间后零 overlap 的真实新屏可追加
- 清空联系人历史同时清空派生摘要

自动摘要默认关闭。

显式开启后：

- 首次约 24 条才摘要
- 之后约每新增 20 条刷新
- 联系人列表排序不因后台摘要跳动
- 摘要失败不影响实时 judgment/reply

## Gate 10 — UI 视觉

重点截图：

- 首页
- 无障碍披露弹窗
- 设置页顶部 / OCR / 历史区
- 知识库联系人列表
- 捕获诊断页
- 微信悬浮球
- judgment 面板
- 3 条候选回复

检查：

- 1440×3200 无裁切
- 系统栏 Insets 正常
- 国风宣纸背景与卡片对比足够
- 字号 / 按钮可点击区合理
- 悬浮窗不遮挡主要聊天内容
- 长模型名 / URL / 错误信息可读

## Release Gate

在下列全部满足前，不合并 PR #1 到 main：

- A 包所有基础真机 Gate PASS
- B 包微信 Accessibility A/B 有明确结论
- Keystore runtime PASS
- 至少一个 judgment provider 实网 PASS
- 至少一个 reply provider 实网 PASS
- 微信 / QQ / X / 飞书回归无 P0/P1
- 不存在错会话填入
- 如果准备 Google Play 分发，必须完成 AccessibilityService declaration / prominent disclosure / Data Safety / privacy policy 的最终一致性检查


## 2026-09-22 真机补充结论

### vivo 应用分身 / 微信 B

在 vivo X100 Ultra（Android 16）实测，主微信运行于 Android user 0，应用分身微信运行于
user 999。Jev 国风包在 user 0 的 AccessibilityService 可以正常 Bound；user 999 中即使通过
ADB 临时执行 `install-existing`，第三方 AccessibilityService 仍不能 Bound，且
`SYSTEM_ALERT_WINDOW` 被系统保持为非 allow 状态。

因此“微信 B 无悬浮窗”不是微信包名适配遗漏，而是 vivo 应用分身的跨-user 隔离。
当前产品基线以主用户中的微信/QQ/飞书/X 为完整能力范围；应用分身后续只能另做受限兼容路径，
不能宣称与主用户完整等价。

### 实测延迟与 2026-09-22 优化

同一真机、同一当前网络与已配置服务下，设置页连通性测试记录：

- Bocha Jev 判断：509 ms。
- GLM-5.3-Flash 回复：1161 ms。

因此体感延迟的主要结构性来源不是 GLM 单次调用本身，而是旧实时链路的 800 ms 防抖以及
“GLM 生成 3 条候选 -> 再等待 Jev 排序”串行尾延迟。当前国风分支已改为：

- 自动分析防抖 800 ms -> 350 ms；
- GLM 候选生成后立即显示“候选回复 · 正在智能排序”；
- Jev 排序完成后原位升级为“推荐回复 · 智能排序”；
- 排序失败时保留可复制/填入的候选，不再让排序故障阻塞回复使用。

### Debug 签名注意事项

Run #33 的已安装国风 APK SHA-256 为
`82d130d950182804cb7c2455f7c9c8adc3170f9efabe962ca593a41efc73244d`，
其签名证书与当前持久开发机的 debug keystore 不一致。已确认这是 GitHub-hosted runner
临时 debug signing 带来的不可覆盖更新问题。

因此 GitHub-hosted PR Gate 产出的 debug APK 只作为 CI 验证产物，不再视为长期真机升级包；
常规 PR 也不再自动上传该 APK。后续真机长期基线应使用持久签名。当前已安装包未卸载，
现有 API Key、知识库与本机配置均未破坏。

本轮最新代码 `feature/guofeng-ui` HEAD `fea3e4d` 已在本机实际执行
`assembleDebug` 成功；生成 APK SHA-256：
`519bac498ef68b961378a754124a37ff5ff601bdc26225218c0ae4858b99eaf2`。


### 历史记录时间语义

最近历史现已显式携带时间信息。每条注入模型的历史形如：

`[记录于 2026-09-20 22:00｜约2天前] 对方：……`

同时注入当前设备时间与时效规则。模型必须：
- 临时计划、日期、地点、状态、截止时间、承诺等按新旧判断，旧记录不能覆盖较新信息；
- 稳定身份、长期偏好可继续作背景，但与新记录冲突时以新记录为准；
- 自动联系人摘要同样接收这些时间信息，避免把过期安排压缩成长期事实。

重要限制：现有 `LogEntry.ts` 是 **Jev 保存/看到该历史的时间**，不是聊天软件保证的原始发送时间。
产品和提示词统一使用“记录于”，不得显示或描述成“发送于”。普通向上滚动使用
`HistoryCaptureHint.CONSERVATIVE`，不会轻易把旧屏幕重新写成当前时间；明确的新消息屏才允许
`NEWEST_SCREEN` 推进历史。

本轮验证：HEAD `ead6416`，`testDebugUnitTest` 22/22 PASS，
`assembleDebug` PASS，APK SHA-256
`9647fc9d608afd605c106800d47acd2409f676a33153df4e1e7ae27260ae5f88`。
