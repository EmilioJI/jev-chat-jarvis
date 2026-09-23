# 微信账号安全架构审计（2026-09-22）

## 1. 结论

本轮目标不是“绕过微信检测”，而是把 JEV 从依赖微信内部 UI / Accessibility Tree 的实现，迁移到用户主动触发、标准 Android 能力优先的架构。

审计前版本存在明确的高风险工程特征：系统/Google 风格 AccessibilityService 类名伪装、`isAccessibilityTool=true`、微信内部资源 ID、后台节点读取、Accessibility 输入框写入。上述机制已经从当前正式分支移除，并由 CI 静态 Gate 防止回归。

当前正式微信路径：

1. 微信不在 AccessibilityService 的 `packageNames` 中；
2. 不订阅微信 Accessibility 事件；
3. 不包含 WeChat 专用 Node Adapter；
4. 不包含 `com.tencent.mm:id/...`；
5. 不通过 Accessibility 自动点击、写字或粘贴；
6. 悬浮球使用标准 Android overlay；
7. 用户主动提供内容，优先剪贴板文本；
8. 本地 ML Kit 截屏 OCR 是可选能力，不需要图像 API Key，不自动触发；
9. 视觉 API 仍为显式可选项；
10. 候选回复当前采用复制，由用户自行粘贴和发送；
11. 历史只记录用户实际提交分析的内容，并带“记录于 + 距今时间”；
12. 联系人关系只来自用户配置/明确资料，不根据昵称猜测。

因此，当前代码已经从“微信内部 UI 自动化助手”转成“微信旁挂的用户主动对话分析器”。

## 2. 逐项审计

| # | 机制 | 审计前 | 当前正式实现 | 分类 |
|---|---|---|---|---|
| 1 | AccessibilityService | 广泛参与微信采集 | 仍存在，但微信不订阅其事件；主要服务 QQ/X/飞书及可选本地截屏 | 建议优化 |
| 2 | rootInActiveWindow | 微信后台/事件路径可读取 | 正式微信内容路径不使用；可选本地截屏底层仍可能为窗口截图取 window id | 建议优化 |
| 3 | 微信 Accessibility Node Tree | 自动读取 | WeChat Adapter 已移除 | 必须修改，已完成 |
| 4 | `com.tencent.mm:id/...` | 使用气泡 ID | 运行源码已移除，CI 禁止回归 | 必须修改，已完成 |
| 5 | Google/system-style SelectToSpeakService | 用类名伪装以获得节点 | 文件、Manifest 注册、旧 XML 均已移除 | 必须修改，已完成 |
| 6 | ACTION_SET_TEXT | 用于填入回复 | 已从服务运行源码移除 | 必须修改，已完成 |
| 7 | ACTION_PASTE | SET_TEXT 失败后的兜底 | 已移除 | 必须修改，已完成 |
| 8 | ACTION_CLICK | 聚焦输入框 | 已移除 | 必须修改，已完成 |
| 9 | Accessibility 截图 | 自动 OCR 兜底可触发 | 仅保留为用户主动本地 OCR / 非微信显式配置路径 | 可以保留，但建议长期迁移 MediaProjection |
| 10 | OCR | 本地 + 可选远程 | 本地 ML Kit 优先且手动；远程 Vision 仅显式选择 | 可以保留 |
| 11 | 悬浮窗 | Accessibility/应用悬浮层 | 有悬浮权限时优先标准 TYPE_APPLICATION_OVERLAY，便于 A/B 共用 | 可以保留 |
| 12 | 自动分析触发 | 微信消息事件可触发 | 微信不订阅事件；全局安全迁移把 autoAnalyze / auto OCR 默认关 | 必须修改，已完成 |
| 13 | 联系人昵称/备注 | 可从微信顶部节点自动取 | 微信正式模式不承诺自动取；用户联系人档案优先，OCR/显式输入仅作线索 | 建议优化 |
| 14 | 聊天历史 | 自动捕获历史 | 只对实际进入分析上下文的内容记录；带记录时间和时效规则 | 可以保留 |
| 15 | 输入框写入 | Accessibility 直接填入 | 正式 UI 已改复制回复；长期由标准 IME 恢复直接输入 | 必须修改，已完成 |
| 16 | 自动发送/手势/Hook/Xposed/LSPosed/Frida/Root/私有协议 | 未发现自动发送、Hook、Root 或私有协议 | CI 增加静态禁用机制；发送始终由用户完成 | 可以保留现状 |
| 17 | 微信 A / B | A 可读树；B(u999)树隔离 | A/B 统一走标准 overlay + 主动内容输入；B 不再需要跨 user 节点能力 | 必须统一，已完成核心架构 |

## 3. 必须修改 / 建议优化 / 可以保留

### 必须修改：已完成

- 删除系统/Google 风格 AccessibilityService 伪装。
- `isAccessibilityTool` 改为 `false`。
- 删除 WeChat 专用 Node Adapter 和内部资源 ID。
- 停止订阅 `com.tencent.mm` Accessibility 事件。
- 删除 Accessibility 输入框点击、SET_TEXT、PASTE。
- 微信停止后台自动读树、自动分析、自动 OCR。
- 旧安装通过一次性安全迁移把自动分析、自动 OCR fallback 关闭。
- 候选回复不自动发送，也不再通过微信控件自动填入。
- CI 增加安全架构 Gate，禁止上述机制回归。

### 建议优化：下一阶段

1. **悬浮窗与 AccessibilityService 解耦**  
   把 overlay 常驻迁到独立前台 `OverlayService`。这样微信只需要悬浮窗即可工作，不必为了悬浮入口长期绑定 AccessibilityService。

2. **标准 IME 恢复“填入”体验**  
   新增可选“小书童·知言输入法”。候选回复交给 IME 后由 `InputConnection.commitText` 写入当前文本编辑器；不查找微信输入框 ID，不模拟点击，用户仍手动发送。

3. **Android Share / PROCESS_TEXT**  
   支持从系统分享菜单把用户选中的聊天文本交给 JEV，降低复制粘贴步骤。

4. **本地截图从 Accessibility 逐步迁 MediaProjection**  
   MediaProjection 每次/每会话由 Android 系统明确授权，工程上更独立于 Accessibility。保留 ML Kit 本地 OCR。

5. **联系人身份输入改为用户明确绑定**  
   正式微信路径不依赖顶部昵称节点。联系人别名由用户设置；若 OCR 能可靠识别标题，只作为候选提示，不自动推断关系。

### 可以保留

- 标准 SYSTEM_ALERT_WINDOW 悬浮入口。
- 国风 UI。
- 本地 ML Kit OCR（用户主动触发）。
- 可选视觉 API（显式选择，默认不需要）。
- Jev 判断 + 回复模型。
- 渐进式候选：先生成候选，再后台排序。
- 关联上下文、联系人笔记。
- 历史记录时间信息和时效规则。
- API Key AndroidKeyStore 加密。
- 发送动作永远由用户完成。

## 4. 替代架构比较

| 架构 | 微信账号风险 | 微信更新破坏 | 用户步骤 | 速度 | 自动昵称/备注 | 最近历史+时间 | 回复写入 | A/B | Android 限制 | 开发复杂度 |
|---|---|---|---|---|---|---|---|---|---|---|
| A. 最小风险 Accessibility | 中 | 中~高 | 低 | 快 | 视节点可用性 | 可做 | 不建议 Accessibility 写入 | B 节点差 | Play/系统敏感 API | 中 |
| B. 点击悬浮球后主动读取 | 中低 | 中 | 1 次点击 | 快 | A 可能，B 不稳定 | 当前屏为主 | 复制/IME | A/B 能用但能力不一致 | 节点权限 | 中 |
| C. 仅截图 + 本地 OCR | 低~中 | 低 | 1~2 次点击 | 中 | 可 OCR，可靠性有限 | 可见屏 + 时间 | 复制/IME | A/B 一致 | FLAG_SECURE/截屏授权 | 中 |
| D. Share / 复制文本 → JEV | 低 | 很低 | 2~3 步 | 快 | 只有用户提供才有 | 用户复制多少就有多少 | 复制/IME | A/B 一致 | 剪贴板/分享限制 | 低 |
| E. AI 输入法 / IME | 输出侧低 | 很低 | 切换/启用输入法 | 很快 | 不负责读取 | 不负责读取 | **标准直接输入** | A/B 一致 | IME 隐私/启用流程 | 中~高 |
| F. Hybrid Active Mode | **较低，推荐** | **低** | 常见 1~2 步 | 快 | 用户档案/OCR 可选 | 显式历史 + 时间 | **IME + 复制兜底** | **A/B 一致** | 标准 Android 权限 | 中~高 |

## 5. 长期正式架构

推荐 **F：Hybrid Active Mode**：

```text
前台助手服务（KeepAliveService）+ OverlayRuntime
        │
        ├─ 分析剪贴板 / Android Share（默认）
        ├─ 本地 ML Kit OCR（用户主动，可选）
        └─ Vision API（用户主动，可选）
                │
                ▼
       Jev 判断 + 候选回复
                │
        ┌───────┴────────┐
        ▼                ▼
     复制回复         小书童 IME
                         │
                         ▼
                 InputConnection.commitText
                         │
                         ▼
                   用户手动发送
```

微信 A 与微信 B 在此架构下没有本质差异：都只需要标准悬浮窗和用户主动输入。vivo 的 user 999 Accessibility 隔离不再是核心障碍。

## 6. 当前剩余风险

- 无法承诺“零封号风险”。任何第三方聊天辅助工具都可能受到平台规则、版本与风控策略变化影响。
- 当前 App 仍包含 AccessibilityService，用于 QQ/X/飞书标准适配和可选系统截屏；微信核心悬浮、通知分析、剪贴板和 IME 路径已经与 AccessibilityService 生命周期解耦。
- Accessibility 截屏虽是标准 Android API，但仍是敏感能力；正式上架需准确披露。
- 本地 OCR 对说话人、昵称、时间分隔的识别存在误差，不能把推断写成事实。
- 剪贴板路径无法自动获得联系人昵称或完整历史，这是降低内部依赖后的明确体验取舍。
- vivo / Android 更新可能改变跨 user overlay 行为，需要版本回归测试。

## 7. 规则与发布注意

Google Play 对非辅助功能类 App 使用 AccessibilityService 有额外披露、用户同意和声明要求；JEV 不属于以帮助残障用户为主要目的的 accessibility tool，因此必须保持 `isAccessibilityTool=false`，并继续维持独立的显著披露和肯定同意流程。

Google Play 官方参考：
- https://support.google.com/googleplay/android-developer/answer/10964491

微信侧不把任何“当前测试未触发风控”解释为长期许可。上线前应重新核对腾讯/微信当期软件许可、个人帐号使用规范和平台政策；产品设计以“不绕过安全机制、不控制微信 UI、不读取内部资源接口”为原则。

## 8. 当前验收标准

本阶段可交付的强制 Gate：

- [x] 无 Google/system-style AccessibilityService 伪装
- [x] `isAccessibilityTool=false`
- [x] Accessibility 配置不订阅 `com.tencent.mm`
- [x] 无 `com.tencent.mm:id/...`
- [x] 无 WeChat Node Adapter
- [x] 无 Accessibility 输入框点击/写入/粘贴
- [x] 无自动发送
- [x] 微信自动分析关闭且无法由微信事件触发
- [x] 微信自动 OCR 关闭
- [x] A/B 均保留标准悬浮入口架构
- [x] 本地 OCR 不要求图像 API Key
- [x] 关系不按昵称猜测
- [x] 历史带记录时间和时效规则
- [x] CI 有安全回归 Gate
- [x] 微信核心 Overlay 生命周期已与 AccessibilityService 解耦，由 KeepAliveService + OverlayRuntime 持有
- [x] 标准 write-only IME 已实现并通过 CI；真机输入/切回体验待 V2366HA 验收
- [ ] 最新安全架构完成 A/B 真机回归（待本阶段 CI 通过后执行）


## 9. 2026-09-23 便捷度恢复实现（已落地）

前一版“纯主动复制/截图”安全架构虽然降低了微信内部依赖，但日常操作成本过高。本轮在**不恢复微信 Node Tree、内部资源 ID、Accessibility 写输入框或自动发送**的前提下，把常用路径重新压缩。

### 9.1 微信通知辅助链

新增标准 Android `NotificationListenerService`：

- 系统授权是设备级“通知访问”，这一点在 UI 中明确披露；
- 代码入口第一步只接受 `com.tencent.mm`，其他 App 通知立即忽略；
- 读取 Android 通知提供的会话标题、消息摘要和通知时间；
- 支持 `EXTRA_TEXT_LINES` 多行摘要；
- 消息时间作为 `Msg.ts` 结构化字段传递，不污染正文；
- 最近通知只保存在进程内存；只有用户另外开启“关联上下文”时，实际进入分析的内容才会进入既有知识库历史；
- 默认是“通知到达 → 悬浮球提示 → 用户点一次才分析”；
- 用户可显式打开“通知到达即自动分析”。

通知路径不会打开微信，不读取微信 Accessibility Tree，不查找微信控件，不模拟触摸。

### 9.2 Overlay 与 Accessibility 生命周期解耦

悬浮 UI 现在由前台助手服务持有进程级 `OverlayRuntime`：

- 微信核心路径不要求开启 AccessibilityService；
- 微信 A / B 均使用标准应用悬浮层；
- AccessibilityService 只作为 QQ / X / 飞书标准适配和可选本地截图能力；
- OverlayController 在进程内保持单实例，前台助手服务重启不会制造第二套悬浮窗；
- “重新分析”和“存联系人”回调按当前数据源拥有者隔离，通知源与 Accessibility 源不会相互清错回调；
- 未启用某项能力时，对应按钮不会显示，避免“按钮可见但实际不可用”。

### 9.3 可选极速模式

两个独立 opt-in：

1. **微信通知到达即自动分析**  
   使用 Android 通知内容直接运行判断与候选生成。

2. **首选回复生成后自动复制**  
   仅当 Jev 排序真正成功时，把排名第 1 的候选放进系统剪贴板并收起面板；排序失败时不会把未排序候选冒充“首选”。

组合后典型路径可缩短为：

```text
微信通知
  → JEV 自动分析/排序
  → 首选回复自动复制
  → 用户打开微信
  → 粘贴
  → 用户发送
```

两个开关都默认关闭。

### 9.4 小书童·快捷填入 IME

新增可选标准 Android `InputMethodService`：

- 候选通过进程内 `ReplyHandoff` 传递，不新建候选回复磁盘数据库；
- “快捷填入”只把选定回复 arm 到内存，并打开 Android 系统输入法选择器；
- 用户明确选择“小书童·快捷填入”后，仅调用标准 `InputConnection.commitText()`；
- 不读取光标前后文字；
- 不读取选中文本；
- 不读取用户手工键入内容；
- 不执行 `performEditorAction`、发送键或任何“发送”动作；
- 写入后尝试切回上一个输入法；
- 一次性 armed reply **60 秒过期**，避免用户取消选择器后很久再切换输入法时意外插入旧回复；
- 候选列表本身在进程内最多保留 10 分钟、最多 3 条。

CI 增加 write-only IME Gate，禁止未来把 IME 扩成文本监控器或发送控制器。

### 9.5 微信 A / B 严格命名空间

通知来源按 Android profile label 构造内部 scope：

```text
com.tencent.mm@<Android profile>
```

联系人匹配对该 scope 采用严格规则：

- A 只能命中带 A scope 的联系人；
- B 只能命中带 B scope 的联系人；
- 未知 profile 不回退到旧的同名微信联系人；
- QQ / X / 飞书等普通 App 保留原有兼容 fallback。

因此即使微信 A 和微信 B 都有一个显示名完全相同的“张三”，也不会仅因为同名而共享关系、摘要或历史。

**真机限制：**代码已经能区分 `StatusBarNotification.user` 暴露的 profile，但 vivo 是否会把应用分身通知以独立 user/profile 交给 user-0 的 NotificationListenerService，必须以 V2366HA 真机结果为准。如果 vivo 不透传 B 的通知，微信 B 仍保留悬浮窗 + 剪贴板 + 可选本地 OCR + IME 路径，但“通知一键分析”不能宣称已支持。

### 9.6 当前操作步数

| 模式 | 新消息分析 | 回复进入输入框 | 发送 |
|---|---|---|---|
| 默认安全 | 通知到达 → 点悬浮球 | 点候选复制 → 粘贴 | 用户 |
| 通知一键 | 通知到达 → 点悬浮球即分析 | 点候选复制 → 粘贴 | 用户 |
| 自动分析 | 通知到达自动分析 | 点候选复制 → 粘贴 | 用户 |
| 自动分析 + 自动复制 | 通知到达自动分析 | 已自动复制 → 粘贴 | 用户 |
| 快捷 IME | 上述任一分析方式 | 点“快捷填入” → 系统选择小书童 → 自动 commitText | 用户 |

因此当前产品不再依赖“先手工复制整段聊天才能使用”；剪贴板和本地 OCR仍作为主动 fallback。

## 10. 最终代码 Gate（2026-09-23）

最终运行代码 HEAD：

`258ba1da1b8086baed0b9b24ca426634c1aa573c`

随后 A/B 严格 profile 隔离与共享 Overlay 生命周期修复已继续提交并通过 CI。最终验收 Run：

- Android UI PR Gate **#143**：SUCCESS
- WeChat safety architecture guard：PASS
- Unit tests + debug APK：PASS
- APK metadata：PASS
- JVM `@Test` 方法：30
- package：`com.jev.probe.guofeng`
- label：`小书童·知言`
- targetSdk：36
- compileSdk：36
- Run #143 APK SHA-256：`dbbe36cb7dd3df610649514e56bbdf62e12bc512900188bec1ef9b589811bfa1`

Run #134 首次失败仅为 GitHub-hosted Runner 下载 Gradle 时连接被重置；按带宽策略只重试失败任务一次，第二次完整 PASS，没有通过增加 Runner 或重复 workflow 规避问题。

### 仍待真机 Gate

代码/CI PASS 不等于主力微信长期验收 PASS。仍需在 V2366HA 上验证：

- 微信 A：通知权限 → 悬浮提示 → 一键分析 → 候选；
- 微信 B：是否能收到独立 profile 的通知回调；
- A/B 同名联系人实际不会串历史；
- 可选“通知到达即分析”；
- 可选“首选自动复制”；
- 小书童 IME 的系统启用、选择、`commitText`、自动切回原键盘；
- 两个微信上均无自动发送；
- 未开 Accessibility 时微信核心功能仍可用；
- 本地 OCR 在未开 Accessibility 时不显示不可用入口。

在这些真机项通过前，PR 继续保持 Draft。
