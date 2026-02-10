# Nimbus Agent 用户模块渐进实施方案

本文档用于指导 `nimbus-agent` 从“临时 userId”平滑演进到“可并发、可分布式”的用户模块。

## 目标

- 去掉硬编码 `demo-user`。
- 建立稳定的用户标识与会话归属。
- 为后续并发治理、分布式记忆、权限体系打基础。

## 阶段 0：最小可用（当天可完成）

### 0.1 用户上下文入口统一
- 在请求入口统一解析 `userId`，优先级：
  1) `X-User-Id` 请求头
  2) 请求体 `userId`
  3) 兜底 `guest-{sessionId}`
- 将解析结果写入 `UserContext`（ThreadLocal 或请求属性）。

### 0.2 会话归属规则
- 将会话键统一为 `userId + sessionId`。
- 明确规则：同 `sessionId` 在不同 `userId` 下必须隔离。

### 0.3 验收标准
- 不传 `userId` 也能跑通。
- 同一 `sessionId`，不同 `userId` 的上下文不串线。

## 阶段 1：落库与基础模型（1~2 天）

### 1.1 数据表设计
- `users`
  - `id` (bigint, pk)
  - `user_key` (varchar, unique)  // 业务用户标识
  - `display_name` (varchar)
  - `status` (tinyint)
  - `created_at`, `updated_at`
- `chat_sessions`
  - `id` (bigint, pk)
  - `session_id` (varchar)
  - `user_id` (bigint)
  - `last_active_at`
  - unique key: (`user_id`, `session_id`)

### 1.2 代码结构建议
- `user` 子包下新增：
  - `User` / `ChatSession` 实体
  - `UserRepository` / `ChatSessionRepository`
  - `UserService`（查找或创建用户）

### 1.3 验收标准
- 首次请求自动创建用户记录。
- 可按 `userId + sessionId` 查到会话归属。

## 阶段 2：接入会话记忆（2~3 天）

### 2.1 记忆键改造
- ChatMemory / Redis key 使用：`nimbus:chat:{userId}:{sessionId}`。
- 所有工具调用上下文都带 `userId`。

### 2.2 会话生命周期
- 增加 `last_active_at` 更新。
- 设置 TTL 清理策略（如 2h/24h，按场景选择）。

### 2.3 验收标准
- 服务重启后（若用 Redis）会话可恢复。
- 多用户并发对话互不影响。

## 阶段 3：并发与稳定性（2~4 天）

### 3.1 用户级限流
- 维度：`userId`。
- 策略：令牌桶或滑动窗口（推荐 Redis + Lua 原子实现）。

### 3.2 外部依赖保护
- 按用户统计 DashScope/QWeather 调用失败率。
- 增加超时、重试上限、熔断与降级文案。

### 3.3 验收标准
- 压测下无明显串线。
- 限流触发时返回可解释错误，不雪崩。

## 阶段 4：权限与审计（后续迭代）

### 4.1 鉴权接入
- 接入 JWT。
- 将 `sub` 映射为 `user_key`。

### 4.2 审计日志
- 记录：`traceId/userId/sessionId/toolTrace/error`。
- 支持按用户快速回放问题链路。

### 4.3 验收标准
- 可根据 `traceId` 完整还原一次请求。
- 用户行为可追踪，可统计。

## 推荐实施顺序

1. 阶段 0（去硬编码、统一入口）
2. 阶段 1（落库、实体与服务）
3. 阶段 2（记忆键改造）
4. 阶段 3（限流熔断）
5. 阶段 4（鉴权审计）

---

## 简历表达建议（用户模块）

- 完成多用户会话隔离方案，基于 `userId + sessionId` 消除上下文串线。
- 建立用户与会话模型，支持自动建档、会话追踪与生命周期管理。
- 在高并发场景下落地用户级限流与外部依赖熔断，提升系统稳定性。
