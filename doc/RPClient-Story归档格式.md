# RPClient Story 归档格式

RPClient 使用 `.rpstory.json` 交换故事正文与 Story 级写作配置。文件采用 UTF-8 JSON，当前主版本为 `1`。

```json
{
  "format": "rpclient_story",
  "version": 1,
  "story": {
    "title": "示例",
    "content": "完整连续正文",
    "memory": "长期事实与大纲",
    "authorNote": "本轮写作方向",
    "summary": "当前剧情摘要"
  },
  "characterHints": [
    {
      "name": "Alice",
      "fingerprint": "sha256",
      "activationMode": "auto",
      "activationKeys": ["Ally"]
    }
  ],
  "lorebookHints": [
    {
      "lorebookName": "Old City",
      "entryName": "Station",
      "fingerprint": "sha256"
    }
  ]
}
```

`story.content` 是章节标题、场景分隔符和正文的唯一事实来源。归档不会保存本地 Room 主键、Provider 配置、API Key、Prompt Inspector 快照或世界书时序状态。

导入时先完成格式和大小校验，不写数据库；用户确认标题后才在单个事务中创建 Story。角色和世界书提示优先按内容指纹唯一匹配，再按名称唯一匹配。存在多个候选时不会自动关联，避免跨设备自增 ID 或重名资源造成错误绑定。

未知主版本、缺少必需字段、非法引用模式以及超过应用限制的文件会被拒绝。导入错误只显示通用原因，不回显完整正文、真实文件路径或堆栈。
