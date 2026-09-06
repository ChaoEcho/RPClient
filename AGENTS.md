# RPClient — AI 指南

## Purpose

RPClient 客户端。**只在本机保留源码，不在本机运行。**

## Start Here

这个项目有自己的开发规范和决策记录，先读它们再动代码：

- `DOCS/RPClient/BUILD_GUIDELINES.md`
- `DOCS/RPClient/DEVELOPMENT.md`
- `DOCS/RPClient/decisions/`（AI 任务归属、开发日志隐私、fork 分支模型）

## Change Boundaries

它是 `source-only`：本机不部署、无端口、无数据。
不要为它添加本机部署配置——要部署先改 catalog 的 `lifecycle` 与 `runtime`。

`decisions/0003-fork-branch-model.md` 定义了分支模型，改分支策略前先读它。

## Verification

```bash
projectctl check RPClient
```

## Relevant Skills

`minimal-engineering`。
