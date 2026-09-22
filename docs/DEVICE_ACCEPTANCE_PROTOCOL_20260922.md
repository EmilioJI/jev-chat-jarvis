# Android 16 真机验收协议 — 2026-09-22

适用仓库：`EmilioJI/jev-chat-jarvis`

## 验收包

### A — 兼容基线 / 当前集成候选

- package: `com.jev.probe.guofeng`
- label: `Jev聊天助手·国风验收`
- `isAccessibilityTool=true`
- source commit: `f2537f6b61ff69c6341a8c7e31d04fa03c416f45`
- Run #27: `35719705538`
- APK SHA-256:
  `42bf16691c25240e2cf1b5ca9270d1dc773c316f3515dd93b07b097805c6fa54`

### B — Play 合规 A/B 实验候选 v3

- package: `com.jev.probe.compliance`
- label: `Jev聊天助手·合规A/B`
- `isAccessibilityTool=false`
- source commit: `73cd437f73965771cf46478b6413c217db9633dd`
- Run #29: `35721226611`
- APK SHA-256:
  `4b04615fa0f73d8ca55b21233df55e4f36d52e61f516ee704d2e4b716520aa62`

B v3 与 A 使用同一最新 transport-hardened 核心代码基线；除 `isAccessibilityTool`
以及为并行安装而使用的 package / launcher label 外，不引入其它业务行为差异。

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

## Gate 3 — 微信 Accessibility A/B（关键 Gate）

A、B 分别：

1. 开启各自无障碍服务
2. 打开同一个微信一对一聊天
3. 保持屏幕停在同一组消息
4. 回到对应 App → 设置 → 捕获诊断
5. 记录：

| 字段 | 期望 |
|---|---|
| 当前应用 | 微信 |
| 包名 | `com.tencent.mm` |
| 适配器 | WeChat |
| 捕获来源 | 控件树 |
| 标题节点 | 已读到 |
| 消息条数 | > 0 |

判定：

- A PASS / B PASS → `isAccessibilityTool=false` 未破坏微信捕获，可进入合规迁移下一步
- A PASS / B FAIL → flag 对当前微信/ROM 存在兼容影响，B 不得合并
- A FAIL / B FAIL → 不是 flag 问题，需查微信版本/伪装机制/ROM
- A FAIL / B PASS → 异常结果，复测后再分析

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
