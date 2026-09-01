# RPClient 完整备份格式 V1

本文定义 RPClient V1 的完整备份文件、Replace Restore 语义以及 WebDAV 备份存储范围。V1 的字段顺序、版本值、入口名称和校验规则构成稳定格式契约。完整备份使用 `.rpbackup` 扩展名，是客户端生成的加密逻辑快照，不是 `primary.sqlite`、WAL 或 SHM 文件的副本。

## 契约摘要

- 文件扩展名：`.rpbackup`
- MIME 类型：`application/octet-stream`
- 逻辑格式标识：`format = "rpclient-backup"`
- 备份格式版本：`backupVersion = 1`
- 外层容器版本：`containerVersion = 1`
- 密码派生：PBKDF2-HMAC-SHA256，V1 导出使用 `200000` 次迭代
- 内容加密：AES-256-GCM
- 解密后的内容：UTF-8 JSON、JSONL 和二进制文件组成的 ZIP
- 恢复语义：只支持完整验证后的 Replace Restore
- WebDAV：可选的加密备份文件存储，不提供同步或合并

> **重要：** Restore 只有在密文完成解密并通过 GCM 认证、ZIP 安全展开、清单、表、JSON 和文件哈希全部验证后，才可以替换当前业务数据。验证失败时不得清空或替换当前业务数据。

## 外层加密容器

`.rpbackup` 的外层是按固定顺序写入的二进制 encrypted envelope。所有版本号和迭代次数字段均为 4 字节大端整数。

| 字段 | 长度与编码 | V1 约定 |
| --- | --- | --- |
| `magic` | 15 字节 ASCII | `RPCLIENT_BACKUP` |
| `containerVersion` | 4 字节大端整数 | `1` |
| `kdfIterations` | 4 字节大端整数 | `200000` |
| `salt` | 16 字节 | 每个备份随机生成 |
| `iv` | 12 字节 | 每个备份随机生成 |
| `encryptedPayload` | 其余全部字节 | AES-256-GCM 密文，末尾包含认证标签 |

`magic` 用于在执行密码派生前快速判断文件是否属于 RPClient 完整备份。V1 的 `containerVersion` 必须为 `1`；不支持的容器版本必须被拒绝。

## 密钥派生与加密

- 使用用户提供的备份密码派生 256-bit AES 密钥。
- KDF 为 PBKDF2-HMAC-SHA256，V1 使用 `200000` 次迭代。
- `salt` 为 16 字节随机值，不在不同备份之间复用。
- AES 使用 GCM 模式，IV 为 12 字节随机值。
- GCM 认证标签为 128 bit，并随密文写入 `encryptedPayload` 的末尾。
- 错误密码、密文被修改或认证标签不匹配时，整个备份视为不可用，不得进入 destructive restore。
- 密钥、派生密钥、密码和底层密码学异常不属于备份文件内容，也不应展示在用户界面或普通日志中。

## 解密后的 ZIP 布局

解密后的 payload 是 ZIP。V1 的固定入口包括清单、偏好快照、19 个业务表和按 SHA-256 内容寻址的文件资产。

```text
manifest.json
preferences.json

tables/
    characters.jsonl
    character_llm_provider_associations.jsonl
    lorebooks.jsonl
    lorebook_entries.jsonl
    chat_sessions.jsonl
    chat_messages.jsonl
    llm_providers.jsonl
    files.jsonl
    group_chat_sessions.jsonl
    group_chat_members.jsonl
    group_chat_messages.jsonl
    group_chat_summaries.jsonl
    regex_scripts.jsonl
    regex_character_authorizations.jsonl
    stories.jsonl
    story_volumes.jsonl
    story_chapters.jsonl
    story_characters.jsonl
    story_lorebook_entries.jsonl

files/
    <sha256>
    <sha256>
    ...
```

表入口使用 UTF-8 JSONL。每一行是一个实体对象；空表使用空文件表示，不使用空 JSON 数组。V1 不把 Room 数据库文件直接放入 ZIP，也不要求恢复端把所有聊天记录一次性载入内存。

## `manifest.json`

V1 清单使用以下字段：

```json
{
  "format": "rpclient-backup",
  "backupVersion": 1,
  "containerVersion": 1,
  "appVersionCode": 0,
  "appVersionName": "",
  "databaseVersion": 5,
  "createdAt": 0,
  "tableCounts": {},
  "fileCount": 0
}
```

字段含义如下：

- `format` 必须为 `rpclient-backup`。
- `backupVersion` 描述解密后逻辑快照的契约版本，V1 必须为 `1`。
- `containerVersion` 描述外层加密容器版本，V1 必须为 `1`。
- `appVersionCode` 和 `appVersionName` 是创建备份时的应用版本元数据。
- `databaseVersion` 是创建备份时的 Room 数据库版本元数据；它与 `backupVersion` 独立，当前实现的值为 `5`，不代表可以直接打开 SQLite 文件。
- `createdAt` 是创建时间的 Unix 时间戳，单位为毫秒。
- `tableCounts` 必须以 19 个表入口名称为完整键集合，并记录每个 JSONL 文件的行数。
- `fileCount` 记录 `files/<sha256>` 中去重后的资产数量，不是 `tables/files.jsonl` 的行数。

## 19 个必需的表入口

以下名称来自 V1 的 `BackupContract.requiredTableEntries`，名称、大小写和路径均属于格式契约：

- `tables/characters.jsonl`
- `tables/character_llm_provider_associations.jsonl`
- `tables/lorebooks.jsonl`
- `tables/lorebook_entries.jsonl`
- `tables/chat_sessions.jsonl`
- `tables/chat_messages.jsonl`
- `tables/llm_providers.jsonl`
- `tables/files.jsonl`
- `tables/group_chat_sessions.jsonl`
- `tables/group_chat_members.jsonl`
- `tables/group_chat_messages.jsonl`
- `tables/group_chat_summaries.jsonl`
- `tables/regex_scripts.jsonl`
- `tables/regex_character_authorizations.jsonl`
- `tables/stories.jsonl`
- `tables/story_volumes.jsonl`
- `tables/story_chapters.jsonl`
- `tables/story_characters.jsonl`
- `tables/story_lorebook_entries.jsonl`

这些表覆盖 RPClient V1 的角色、角色与模型关联、世界书、会话、消息、模型配置、文件引用、群聊、Regex 和 Story 业务数据。JSONL 中保存原始实体字段与 ID，恢复时保留这些 ID，以保持外键和应用设置中的 Provider、文件等引用关系。

## `preferences.json`

`preferences.json` 是显式版本化的 `BackupPreferencesSnapshot`，不是整个 Kotpref 或 SharedPreferences 文件的机械复制。它包含恢复用户体验所需的应用设置，例如：

- 当前、摘要和图片提示使用的模型配置 ID
- TTS、图片生成和模型服务的 URL、模型、参数，以及用户配置的 API Key
- 系统 TTS、提示词、世界书格式、摘要和上下文处理设置
- 用户名、头像引用、用户描述、流式输出和其他可迁移的用户偏好

由于完整备份需要在换机后继续使用原来的模型配置，显式偏好快照可以包含用户填写的模型、TTS 或图片服务 API Key。它们受整个 `.rpbackup` 的密码加密保护，但用户仍应妥善保管备份文件和备份密码。

以下本机状态不会通过 `preferences.json` 恢复：

- migration bookkeeping
- 当前设备的 installation-specific identity
- `llmDefaultProvidersInitialized` 的旧值；恢复完成后由应用将其设为 `true`
- AndroidKeyStore 密钥和 SecureSecretStore 内容
- WebDAV 密码以及本机记住的备份密码

## 文件资产与去重

`tables/files.jsonl` 保存 `FileEntity` 记录及其 hash。物理内容按 SHA-256 hash 去重并写入 `files/<sha256>`：

- `<sha256>` 必须是 64 位小写十六进制字符串。
- 相同 hash 的多个 `FileEntity` 只写入一份物理资产。
- 资产保留原始二进制内容，不重新编码、解码 Bitmap 或重新压缩图片。
- 每个 `FileEntity.hash` 都必须在 `files/` 中找到对应资产。
- 恢复验证会重新计算每个资产的 SHA-256，并要求结果与文件名一致。
- 创建完整备份时，如果被引用的物理文件缺失，备份创建失败，而不是生成缺少资产的“成功”备份。

恢复时保留 `FileEntity.uuid` 和 hash 关系，不通过普通保存流程重新生成文件 UUID。

## 不进入 `.rpbackup` 的数据

以下数据明确排除在完整备份之外：

- `request_logs.sqlite` 及其 WAL、SHM 和 journal 文件
- TTS cache、普通 app cache 和 restore staging 临时文件
- WebDAV 密码和本机记住的备份密码
- AndroidKeyStore 密钥、SecureSecretStore 自身及其中保存的加密秘密
- installation-specific ID
- migration bookkeeping

因此，`.rpbackup` 不包含请求日志，也不携带用于解开本机记住密码的 AndroidKeyStore 密钥。请求日志仍由本地请求日志功能单独管理；它们不属于 V1 的 19 个业务表。

## Replace Restore 语义

RPClient V1 只支持 **Replace Restore**：使用备份中的完整业务快照替换当前 RPClient 业务数据，不支持 Merge、ID remap、冲突解决或两台设备之间的双向同步。

恢复必须遵守以下边界：

- 选择本地文件或下载 WebDAV 文件后，先复制到应用私有的临时 staging。
- 先完整读取外层容器，检查 magic 和容器版本，派生密钥并完成 AES-GCM 解密与标签认证。
- ZIP 展开必须是安全展开，拒绝绝对路径、目录穿越、反斜杠路径和重复入口等危险情况。
- 解析并验证 `manifest.json` 与 `preferences.json`。
- 检查 `backupVersion`、`containerVersion`、19 个必需表入口和 `tableCounts`。
- 逐行解析全部 JSONL，并要求实际行数与清单一致；空行、非法 JSON 或无法按对应 V1 实体解析的内容均使验证失败。
- 检查 `FileEntity` 引用的 hash 集合、每个 `files/<sha256>` 的文件名和实际 SHA-256，并检查 `fileCount`。
- 验证全部成功后，才准备新的物理文件。新资产先经过临时文件和内容校验；已有相同 hash 的物理文件可以复用。
- 在一次 Room transaction 中执行替换：先按外键关系 child-first 删除旧业务行，再 parent-first 插入备份行，并保留所有原始主键、外键和文件 UUID。
- 数据表恢复成功后应用 `BackupPreferencesSnapshot`；migration bookkeeping 和当前设备 installation identity 不被覆盖。
- 数据库与偏好应用成功后，再按恢复后的 `FileEntity` hash 集清理不再引用的孤儿物理文件。孤儿清理失败不反向撤销已经成功提交的业务数据。
- 无论成功、验证失败、取消还是恢复异常，包含解密明文的临时 staging 都必须清理。

整个过程中，当前业务数据只有在完整验证完成后才进入替换阶段。密码错误、格式错误、数据校验失败或资产缺失都不得先清空当前业务数据。

## V1 兼容性边界

- `backupVersion = 1` 与 `containerVersion = 1` 是 V1 的精确版本要求。
- 当前实现只承诺读取本格式 V1；不承诺读取未来版本，也不承诺第三方自行生成的相似文件可以恢复。
- `databaseVersion` 只是清单元数据，Room 数据库版本变化不自动等同于备份格式版本变化。
- V1 备份是逻辑快照，不是可由其他 SQLite 工具直接打开的数据库文件。
- 未来格式变化需要新的明确备份契约或显式迁移；不能把未知版本当作 V1 继续恢复。

## WebDAV V1 范围

WebDAV 在 V1 中只是用户配置的备份存储位置。RPClient 先在本机生成并加密 `.rpbackup`，然后按用户主动发起的操作传输该文件。

V1 使用 HTTP Basic Auth，并支持以下有限操作：

- `PROPFIND`：测试连接和列出备份目录中的 `.rpbackup` 文件
- `MKCOL`：按配置创建备份目录层级
- `PUT`：上传一个加密备份文件
- `GET`：下载一个加密备份文件
- `DELETE`：删除一个远端备份文件

WebDAV 密码只作为 Basic Auth 凭据使用；如果用户选择在本机记住，它由 AndroidKeyStore 保护的本地秘密存储加密保存，不进入 `.rpbackup`。WebDAV 服务端不属于 RPClient 的托管后端；V1 也不定义服务端加密、自动云备份、同步、合并、冲突解决、定时任务、增量 diff 或 WorkManager 后台调度。

远端 WebDAV 文件是已经在客户端加密的 `.rpbackup`。用户仍需信任自己配置的 WebDAV 服务、妥善保管 Basic Auth 凭据和备份密码，并自行管理远端文件的留存与删除。
