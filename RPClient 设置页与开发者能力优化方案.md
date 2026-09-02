# RPClient 设置页与开发者能力优化方案

## 一、总体目标

本次优化重点不是增加新的业务能力，而是解决目前设置模块存在的三个核心问题：

1. **设置首页信息密度过高**
   - 模型选择、生成参数、图片、语音、请求行为、世界书预算、摘要记忆全部铺在一级页面。
   - 页面非常长，设置项之间缺乏明确层级。

2. **相同概念重复配置**
   - 对话模型已经有完整模型编辑页。
   - 设置首页又额外暴露 Temperature、Top P、Max Tokens、Context 等生成参数。
   - 功能上属于“快捷修改”，但视觉上像两套配置系统。

3. **不同 AI 服务配置页面风格不统一**
   - 对话模型页面已经相对成熟。
   - 图片模型仍是表单式配置。
   - TTS 又使用另一套 Provider Card + 表单。
   - 用户会感觉这是三个独立开发出来的功能，而不是同一套“模型配置”。

本次原则：

> 一级设置页负责“找到功能和查看状态”，二级页负责“真正配置”。

同时：

> 不因为 UI 统一而统一底层数据模型。

图片模型暂时只有一种 OpenAI Compatible 服务，就继续保持单配置；语音继续使用现有 System / Mimo / Azure 数据结构。只统一交互和视觉，不建立新的 Provider 抽象层。

---

# 二、设置首页重新设计

## 2.1 推荐信息架构

设置首页调整为：

### 用户

- 用户身份与人设

### 模型配置

- 对话模型
- 图片模型
- 语音模型

### 提示词与上下文

- 系统提示词预设
- 请求与对话策略
- 世界书预算
- 摘要记忆

### 数据与开发

- 备份与恢复
- 聊天数据管理
- 开发者模式
- 关于

一级页面不再直接出现大块参数表单。

---

# 三、模型配置

## 3.1 设置首页模型区域

当前 `MainLayout.kt` 中：

- `ProviderCard`
- `ParameterPanel`
- `GenerationCapabilitiesPanel`

应从一级设置页重新组织。

新的首页只需要一个统一的 `RpSettingsGroup`：

| 项目 | 副标题建议 | 点击 |
|---|---|---|
| 对话模型 | 当前模型名称，例如 `DeepSeek V3 · deepseek-chat` | 对话模型管理 |
| 图片模型 | 当前模型，例如 `gpt-image-1` / 未配置 | 图片模型配置 |
| 语音模型 | 当前服务，例如 `Mimo · zh-CN` | 语音模型配置 |

建议图标分别使用：

- Chat / SmartToy
- Image
- VolumeUp

三个入口视觉完全一致。

### 一级页面删除

删除一级页面中的：

- 对话 Provider 卡片列表
- Temperature
- Top P
- Max Tokens
- Context Tokens
- 图片生成单独区域
- TTS 单独区域

这些都不应该长期占据一级页面空间。

---

# 四、对话模型

现有对话模型体系已经是三个模型配置页面里最成熟的，因此它应该成为其他页面的设计基准。

现有：

`LLMProviderListLayout.kt`

和：

`LLMProviderEditLayout.kt`

已经具备：

- 模型卡片
- Provider 图标
- 启用状态
- 模型名称
- Base URL
- 新建
- 编辑
- 基础信息
- 参数
- 高级设置折叠
- 测试连接

这个方向保留。

## 4.1 需要补充“当前模型”概念

现在主设置页面承担了：

`MainUiIntent.SelectProvider`

也就是说，如果把主页面模型列表删掉，就需要把“当前默认模型选择”迁移到模型管理页。

推荐：

模型卡片增加：

> 当前

状态 Badge。

例如：

**DeepSeek V3**

`deepseek-chat`

`OpenAI Compatible`　`当前`

而其他启用模型：

`可用`

### 操作逻辑

点击卡片：

> 编辑模型

而不是切换默认模型。

模型卡片中提供一个明确操作：

> 设为当前

或者通过 Radio / Check 控件选择。

避免出现：

> 点击卡片到底是“使用这个模型”还是“编辑这个模型”

这种交互歧义。

---

# 五、图片模型页面

当前：

`ImageGenerationSettingsLayout.kt`

结构是：

- ServicePanel
- PromptModelPanel
- StylePanel

本身功能没有明显问题，主要问题是视觉仍然属于传统表单页面。

## 5.1 不修改图片模型数据模型

暂时不要为了和 LLM 一致引入：

- ImageProvider
- 多 Provider
- Provider Repository
- 新数据库表

当前单配置结构继续保留。

这是本次应该明确避免的过度设计。

---

## 5.2 页面调整

页面视觉参考 `LLMProviderEditLayout`。

顶部：

> 图片模型

副标题：

> 配置图片生成服务与默认生成行为

### 第一组：模型服务

使用 `RpPanel`。

内容：

- Base URL
- API Key
- Model

API Key 建议参考对话模型的：

`ModernCredentialControl`

而不是一直显示一个 Password TextField。

这样图片、对话服务的密钥配置体验一致。

---

### 第二组：生成参数

包含：

- 图片尺寸
- 最大并发请求数

不要和服务连接字段混在一起。

例如：

> 生成参数  
> 图片尺寸　1024x1024  
> 最大并发　2

---

### 第三组：提示词模型

当前：

`PromptModelPanel`

保留逻辑。

但是可以变成一个紧凑设置项：

> 提示词优化模型  
> 跟随当前对话模型

点击展开选择。

---

### 第四组：风格设置

目前：

- Scene Style Prompt
- Avatar Style Prompt

属于低频高级设置。

建议默认折叠：

> 风格提示词  
> 场景和头像生成时附加的默认风格

右侧箭头：

`⌄ / ›`

展开后显示两个 Prompt 编辑器。

---

## 5.3 保存方式

沿用对话模型编辑页：

右上角：

> 保存

不要在页面中间再增加 Save Button。

以后所有“模型编辑页面”统一：

> 顶部右上角保存。

---

# 六、语音模型页面

当前：

`TtsSettingsLayout.kt`

包含：

- System
- Mimo
- Azure
- Preview
- Provider-specific fields

功能完整，但 UI 层级比较散。

## 6.1 Provider 选择

继续保留：

- 系统语音
- Mimo
- Azure

不调整现有 `TtsProviderType`。

但 Provider Card 改成和对话模型卡片类似的视觉。

卡片显示：

**Mimo**

OpenAI Compatible TTS

`当前`

或者：

**系统语音**

Android System TTS

`当前`

---

## 6.2 选中 Provider 后的配置结构

### 基础配置

Mimo：

- Base URL
- API Key
- Model

Azure：

- API Key
- Region

System：

不显示网络配置。

---

### 语音参数

例如：

- Voice
- Language
- Speech Rate
- Pitch
- Temperature

根据 Provider 动态显示。

---

### 高级设置

例如 Mimo：

- Instructions
- Streaming

放进：

> 高级设置

默认折叠。

---

### 语音测试

当前 Preview Text 一直占据页面空间。

建议放到底部：

> 语音测试

折叠区域。

展开：

- 测试文本
- 播放按钮
- 停止按钮

这样日常配置时页面会紧凑很多。

---

# 七、请求行为与示例对话策略

目前 `PromptBehaviorPanel` 是一级页面最大的区域之一。

里面混杂：

- Prompt Post Processing
- Example Dialogue Behavior
- Include Think
- Context Trimming Alert
- Streaming Response

这些参数都是合理的，但不适合全部显示在一级设置页。

## 推荐方案：二级页面

一级页面只显示：

> 请求与对话策略

副标题可以根据当前配置动态生成：

> 严格后处理 · 普通示例对话 · 流式响应

点击进入：

> 请求与对话策略

二级页面。

---

## 7.1 二级页面结构

### 请求处理

- Prompt Post Processing Mode

### 示例对话

- Normal
- Pinned
- Disabled

### 上下文行为

- 将 Think 加入上下文
- 上下文裁剪提醒

### 响应行为

- 流式响应

其中：

Prompt Post Processing Mode 可以继续使用当前模式卡片。

---

## 7.2 是否需要继续折叠

建议：

二级页面内部仍然使用折叠。

例如：

> 请求处理　　　　　Strict　⌄

> 示例对话　　　　　Normal　⌄

> 上下文行为　　　　　　　⌄

> 响应行为　　　　　　　　⌄

这样未来增加更多 Prompt 策略时不会重新把页面撑爆。

---

# 八、世界书预算

目前 `WorldInfoBudgetPanel` 直接显示：

- Context Percent Slider
- Budget Cap
- Overflow Alert

同样下沉到二级页面。

一级：

> 世界书预算

副标题：

> 上下文 25% · 最大 2048 Tokens

点击进入：

> 世界书预算

二级页面继续保留当前参数。

这个页面参数很少，因此没有必要强制折叠。

---

# 九、摘要记忆

目前 `SummaryPanel` 是另一个非常大的一级设置块。

包含：

- General / Conversation Tab
- Summary Provider
- Words Limit
- Response Tokens
- Auto Summary
- Trigger Message Count
- Max Messages
- Injection Position
- Injection Role
- Injection Depth

应该完整移动到：

> 摘要记忆

二级页面。

---

## 9.1 二级页面结构

建议取消现在顶部大 Tab 占空间的方式，改成两个折叠设置组：

### 摘要模型

显示摘要：

> 跟随当前对话模型 · 500 字

展开：

- Summary Provider
- Target Words
- Response Tokens

### 自动摘要

显示摘要：

> 已启用 · 每 20 条消息更新

展开：

- Enable
- Trigger Message Count
- Max Messages Per Request
- Injection Position
- Injection Depth
- Injection Role

这样页面视觉会更接近 Android 系统设置，而不是配置后台。

---

# 十、可折叠组件

现有 `LLMProviderEditLayout.kt` 已经存在：

`CollapsibleAdvancedPanel`

说明工程本身已经接受这种交互。

建议不要每个页面复制一套实现。

当第二个页面开始使用折叠时，再抽一个通用组件：

`RpCollapsibleSettingsGroup`

建议接口概念：

- title
- subtitle
- summary
- leadingIcon
- initiallyExpanded
- content

行为：

- 整行可点击
- 右侧箭头旋转
- `animateContentSize`
- 默认高级项折叠
- 展开状态使用 `rememberSaveable`

不要引入新的复杂 Settings DSL。

一个简单 Compose Component 就足够。

---

# 十一、备份与恢复

这里有两个独立问题：

1. 自动请求远端
2. 按钮语义和布局混乱

---

# 十二、WebDAV 不再自动加载

当前 `BackupViewModel.onInit()`：

初始化完成后，如果 WebDAV 配置完整，会直接：

`onRefreshWebDav()`

也就是说：

> 打开页面 = 发起网络请求。

这个设计应该删除。

新的逻辑：

`Init`

只负责读取：

- Base URL
- Username
- Password
- Remote Path
- Last Backup Time

然后立即显示页面。

**不进行任何 WebDAV 网络访问。**

只有用户点击：

> 加载远端备份

才执行：

`RefreshWebDav`

---

# 十三、WebDAV UI 调整

目前配置区域是：

- 保存配置
- 测试
- 刷新

三个操作挤在配置区域。

而“刷新”实际上不是普通页面刷新，而是：

> 请求 WebDAV 并列出远端备份。

因此文案本身容易误解。

---

## 推荐布局

### WebDAV 连接

字段：

- 服务地址
- 用户名
- 密码
- 远端目录

底部两个按钮：

> 保存配置

> 测试连接

其中：

**保存配置**

只表示：

> 保存 WebDAV 地址、账号等设置。

绝对不上传任何备份。

---

### 远端备份

单独一个 Section。

标题：

> 远端备份

右上角：

> 刷新

第一次打开时：

> 尚未加载远端备份  
> 为避免进入页面时产生网络请求，请手动加载远端列表。

按钮：

> 加载远端备份

第一次成功加载以后，可以变成：

> 刷新列表

这种语义比“拉取”更准确。

因为当前 `RefreshWebDav` 只是：

`listBackups`

并没有下载备份文件。

真正恢复某一个备份时才发生 Download。

---

# 十四、备份页面最终结构

### 本地备份

- 创建完整备份
- 从文件恢复
- 最近备份时间

### WebDAV 连接

- 地址
- 用户名
- 密码
- 路径
- 保存配置
- 测试连接

### 远端备份

- 加载 / 刷新列表
- 上传新备份
- 远端备份列表

这样三种概念不会再混：

> 配置连接

> 创建备份

> 操作远端备份

---

# 十五、调试模式问题分析

这里目前不是单纯 UI 问题。

代码现状显示：

`AppModel.debugModeEnabled`

主要控制：

`LLMRequestLogRepository`

其逻辑是：

> Debug 开启 → 保存 LLM 原始 Request / Response JSON。

现有工程中没有完整的统一应用 Logger。

也就是说：

> 不是“App 日志已经存在但页面没展示”。

而是：

> App 本身目前基本没有形成统一、可供 UI 展示的内部运行日志。

因此现有“调试模式”实际上更准确的名字应该是：

> LLM 请求记录

而不是完整 Debug Mode。

---

# 十六、开发者模式设计

建议把：

> 调试模式

升级成：

> 开发者模式

一级设置显示：

> 开发者模式  
> 查看应用运行日志、AI 请求以及故障信息

开启后进入二级：

> 开发者工具

---

# 十七、开发者工具页面

建议分成两个独立入口。

### 应用日志

用于 App 自身运行状态。

### AI 请求日志

复用现在的：

`RequestLogActivity`

即：

- LLM Request JSON
- LLM Response JSON

这样不要把结构化 App Log 和巨大 JSON Payload 混成一个列表。

---

# 十八、应用日志底层

建议增加非常轻量的：

`AppLogger`

而不是直接到处使用：

`android.util.Log`

业务代码统一：

`AppLogger.d(...)`

`AppLogger.i(...)`

`AppLogger.w(...)`

`AppLogger.e(...)`

日志至少包含：

- timestamp
- level
- tag/module
- message
- throwable summary

例如：

> 12:08:41.332 I Backup — WebDAV refresh started

> 12:08:43.821 I Backup — Loaded 12 remote backups

> 12:10:02.125 E ImageGeneration — Request failed: HTTP 502

---

# 十九、日志应该记录什么

第一版只记录真正有调试价值的关键路径。

### App

- App 启动
- 数据升级
- 初始化异常

### Chat / Generation

- 开始生成
- 生成完成
- 取消
- 超时
- Provider
- 耗时
- Error 类型

不要默认记录完整 Prompt。

---

### Image

- 请求开始
- 模型
- 图片尺寸
- 请求成功 / 失败
- 耗时

---

### TTS

- Provider
- 开始合成
- 播放
- 停止
- 网络 / Audio 错误

---

### Backup / WebDAV

- 保存配置
- 测试连接
- 加载列表
- 上传
- 下载
- 删除
- Restore
- 各操作耗时与错误

---

### Database

只记录：

- Migration
- 数据库异常

不要记录数据库正文。

---

# 二十、HTTP 调试日志

目前项目已经有共享 `OkHttpClient`。

可以增加一个轻量 Interceptor / EventListener：

记录：

- METHOD
- Host / Path
- Status Code
- Duration
- Network Failure

例如：

> POST api.deepseek.com/chat/completions → 200 · 2843ms

但必须默认过滤：

- Authorization
- API Key
- Cookie
- Request Body
- Response Body

否则开发者日志本身会变成敏感信息泄漏点。

LLM 完整 Body 仍由现在独立的：

`LLMRequestLogRepository`

负责。

---

# 二十一、应用日志存储

第一版不建议再创建 Room 表。

这样会引入：

- Entity
- DAO
- Migration
- Repository
- DB Version

对于开发日志明显过重。

推荐：

### 内存 Ring Buffer

例如：

1000～2000 条。

### 可选滚动日志文件

开发者模式开启时：

`files/debug/app.log`

例如：

- 单文件 2 MB
- 最多保留 3 个

达到大小：

`app.log`
→ `app.1.log`
→ `app.2.log`

这样实现简单，也方便：

> 导出诊断日志

---

# 二十二、应用日志 Viewer

UI 可以复用 `RequestLogLayout` 的一些视觉思路。

顶部：

> 应用日志

Toolbar：

- 搜索
- 清除
- 导出

过滤：

`全部 | Info | Warning | Error`

以及 Module：

- App
- Chat
- LLM
- Image
- TTS
- Backup
- Database

日志卡片：

> 12:30:11 ERROR  
> ImageGeneration  
> HTTP 502 when generating image

点击展开：

- 完整 Message
- Exception
- Stack Trace

---

# 二十三、开发者模式隐私设计

开发者模式必须明确区分：

### 应用运行日志

默认安全记录。

### 记录 LLM 原始请求

属于敏感选项。

建议 Developer 页面：

> 开发者模式　　　　　 ON

> 应用运行日志　　　　 ON

> 记录 AI 原始请求　　 OFF

并提示：

> 原始 AI 请求可能包含角色设定、聊天内容和提示词，仅建议调试时开启。

现在 `debugModeEnabled` 可以逐渐被重新定义。

最小修改方案也可以暂时保持：

`debugModeEnabled`

继续控制 LLM Request Log，

另增加：

`developerLoggingEnabled`

控制 App Logger。

后续再统一命名。

不要在这次为了变量名字做大规模迁移。

---

# 二十四、主设置页面目标效果

最终一级设置应该类似：

## 用户

[ 用户身份与人设　　　　　　　　　› ]

## 模型配置

[ 对话模型  
  DeepSeek V3 · deepseek-chat　　　　› ]

────────────────

[ 图片模型  
  gpt-image-1　　　　　　　　　　　› ]

────────────────

[ 语音模型  
  Mimo · default　　　　　　　　　　› ]

## 提示词与上下文

[ 系统提示词预设　　　　　　　　　› ]

────────────────

[ 请求与对话策略  
  Strict · Normal · Streaming　　　　› ]

────────────────

[ 世界书预算  
  上下文 25% · 2048 Tokens　　　　　› ]

────────────────

[ 摘要记忆  
  自动摘要已开启 · 每 20 条　　　　　› ]

## 数据与开发

[ 备份与恢复　　　　　　　　　　　› ]

[ 聊天数据管理　　　　　　　　　　› ]

[ 开发者模式　　　　　　　　　　　› ]

[ 关于　　　　　　　　　　　　　　› ]

一级页面长度会明显减少。

---

# 二十五、代码层实施建议

## 第一阶段：设置首页瘦身

主要修改：

`feature/main/ui/MainLayout.kt`

删除一级页面直接渲染：

- `ProviderCard`
- `ParameterPanel`
- `PromptBehaviorPanel`
- `WorldInfoBudgetPanel`
- `SummaryPanel`

改成 Entry Tile。

保留现有 State 暂时没有问题。

不要第一阶段就删除：

- `MainGenerationParametersState`
- Prompt State
- Summary State

避免 UI 重构同时引发 ViewModel 大改。

等二级页稳定后再进行无用 State 清理。

---

## 第二阶段：模型页面视觉统一

调整：

`feature/imagegeneration/ui/ImageGenerationSettingsLayout.kt`

`feature/tts/ui/TtsSettingsLayout.kt`

参考：

`feature/llmprovideredit/ui/LLMProviderEditLayout.kt`

重点统一：

- `AppTopBar`
- `RpPageTitle`
- `RpPanel`
- `RpSectionHeader`
- 输入框 Shape
- Credential Control
- Collapsible Advanced Panel
- Save / Test 行为位置

不要统一底层 Provider 数据结构。

---

## 第三阶段：设置二级页面

建议新增独立设置页面：

- PromptBehaviorSettings
- WorldInfoBudgetSettings
- SummaryMemorySettings

因为现在 `MainLayout.kt` 已经非常大。

不要继续把多个完整二级页面塞进 `MainLayout.kt`。

每个页面遵循当前 Feature 模式：

`Activity`

`ViewModel`

`UiState`

`UiIntent`

`Layout`

但是业务数据继续读写现有：

`AppModel`

和：

`LLMRepository`

不创建新的 Settings Repository，除非以后确实出现重复。

---

# 二十六、通用折叠组件

当 Prompt、Summary、Image、TTS 都开始出现折叠之后，再从现有：

`CollapsibleAdvancedPanel`

抽出：

`RpCollapsibleSettingsGroup`

只抽 UI。

不要把业务逻辑做成一个所谓：

`UniversalSettingsFramework`

没有必要。

---

# 二十七、WebDAV

修改：

`BackupViewModel.onInit`

要求：

> 初始化只加载本地配置，不调用 `RefreshWebDav`。

调整：

`BackupLayout.kt`

按钮职责重新分组：

连接配置：

- 保存配置
- 测试连接

远端区域：

- 加载远端备份 / 刷新列表
- 上传新备份

---

# 二十八、Developer Logging

新增轻量模块，例如：

`libs/debug/AppLogger.kt`

`libs/debug/AppLogStore.kt`

以后需要 UI 再增加：

`feature/developerlog/...`

第一版 Logger 不需要引入第三方库。

也暂时不需要 Timber。

Android `Log` 可以作为输出 Sink，同时写入自己的 Ring Buffer / File Store。

核心目标是：

> 所有希望在 App 内看到的日志，都必须通过 AppLogger 主动记录。

因为普通 Android App 无法简单依赖“读取整个系统 Logcat”来实现这种开发者控制台。

---

# 二十九、复杂度控制

本次明确不做以下事情：

1. 不统一 Chat / Image / TTS 的底层 Provider Model。
2. 不新增所谓 AIService / UniversalProvider 抽象。
3. 不为了日志增加新的 Room 数据库。
4. 不重新实现整套 Settings Framework。
5. 不改现有 AI 请求逻辑。
6. 不修改模型配置持久化格式。
7. 不同时重构 MainViewModel。
8. 不把 UI 优化扩散成整个项目架构重构。

本次只解决：

> 设置层级、页面一致性、WebDAV 请求行为、开发日志可观察性。

---

# 三十、实施优先级

## P0

必须优先完成：

- 设置首页模型区域改为：
  - 对话模型
  - 图片模型
  - 语音模型
- 一级页面删除生成参数重复配置
- 请求行为下沉
- 世界书预算下沉
- 摘要记忆下沉
- WebDAV 不再进入页面自动请求
- WebDAV 按钮重新分组和命名

完成这些后，设置页体验就会出现非常明显的改善。

---

## P1

随后完成：

- 图片模型 UI 对齐对话模型
- TTS UI 对齐对话模型
- 通用折叠组件

---

## P2

开发者能力：

- Developer Mode
- AppLogger
- App Log Viewer
- HTTP metadata logging
- 日志导出

LLM Request Log 保持独立。

---

# 三十一、验收标准

### 设置首页

- 用户进入设置页面时，不出现 Temperature / Top P 等具体生成参数。
- 一级页面不再出现巨型 Request Behavior / Summary 表单。
- 模型区域只有对话、图片、语音三个明确入口。
- 页面能够在较短距离内浏览完所有一级分类。

### 模型页面

- Chat / Image / Voice 使用明显一致的视觉语言。
- 基础配置默认展开。
- 高级配置默认折叠。
- Save 操作位置一致。

### WebDAV

- 打开备份页面时不产生 WebDAV 网络请求。
- 用户主动点击加载/刷新后才请求远端。
- “保存配置”不会让用户误以为是在上传备份。
- “上传备份”和“保存 WebDAV 配置”视觉上属于两个完全不同的区域。

### 开发者模式

- 能查看 App 自身运行日志，而不仅是 LLM JSON。
- 能看到 Backup / Image / TTS / Chat 等关键业务异常。
- 日志默认不记录 API Key、Authorization 和完整 Prompt。
- 原始 LLM 请求日志仍然单独控制。