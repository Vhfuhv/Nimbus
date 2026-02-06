# Nimbus Agent 周末 TODO（个人笔记）

目标：把当前“对话 wrapper”升级为**可解释、可回归、可演示**的 Spring AI Agent（DashScope Qwen）。

> 现状速记：已有天气查询链路（城市→LocationId→和风→穿衣规则），以及 `POST /nimbus/chat`（LLM 总结）。

---

## 0. 开工前准备（30min）

- [ ] 本地 JDK 使用 17+（项目编译目标 17）
- [ ] 启动方式统一一种（任选其一）：
  - 环境变量：`DASHSCOPE_API_KEY`、`QWEATHER_API_KEY`
  - 启动参数：`--DASHSCOPE_API_KEY=... --QWEATHER_API_KEY=...`
- [ ] 写一个 curl/HTTPie/Postman 请求模板，确保能稳定打通：
  - `POST /nimbus/chat`（作为对照基线）
- [ ] 清点当前接口契约（请求/响应字段），后续 agent 接口尽量兼容扩展。

---

## 1. 定义“最小可用 Agent”（MVP Agent）

### 1.1 行为目标（必须能演示）

- [ ] 支持多轮：用户说“明天呢/那后天呢/换成上海”，能复用上一轮上下文
- [ ] 缺参追问：没城市就追问城市；没日期就默认今天并说明
- [ ] 工具调用：模型**决定**何时调用工具（而不是 Controller 写死顺序）
- [ ] 可观测：每次请求返回 `traceId` + `toolTrace`，日志可定位慢点/失败点

### 1.2 新增接口（建议）

- [ ] `POST /nimbus/agent/chat`
  - Request：`{ sessionId?, message, userId? }`
  - Response：`{ success, content, sessionId, traceId, toolTrace[], city?, weather?, clothingAdvice?, errorMessage? }`

---

## 2. 会话状态（Session Memory）

### 2.1 先做内存版（当天可完成）

- [ ] 设计 SessionStore 接口：
  - `get(sessionId)` / `append(sessionId, role, content)` / `summarizeIfNeeded(sessionId)`
- [ ] 策略：
  - 最近 N 轮（例如 10 条），超出后做摘要（summary 作为 system/context）
  - TTL（例如 30min/2h）
- [ ] 在 Response 回传 `sessionId`（如果请求没带则创建）

### 2.2 预留 Redis 切换点（下周再接也行）

- [ ] Key 设计（示例）：
  - `nimbus:chat:{sessionId}:messages`
  - `nimbus:chat:{sessionId}:summary`
  - `EXPIRE` 同 TTL

---

## 3. 工具化（Tool Calling）

把你现有的“确定性能力”变成工具，让模型调用。

### 3.1 必备工具（建议 3 个起步）

- [ ] `extract_city`
  - input：`{ text }`
  - output：`{ found, cityName?, locationId?, confidence?, candidates? }`
- [ ] `get_weather_today`
  - input：`{ locationId }`
  - output：`DailyWeather`（或你已有 WeatherResponse 的 today）
- [ ] `get_clothing_advice`
  - input：`{ dailyWeather }`
  - output：`ClothingAdvice`

### 3.2 工具调用失败策略（必须）

- [ ] `extract_city` 失败 → 追问“你要查哪个城市？”
- [ ] 天气 API 失败 → 给出降级话术（“暂时不可用”）+ 建议稍后重试
- [ ] 工具异常要写入 `toolTrace`（name、status、durationMs、error 摘要）

---

## 4. Prompt / Agent 指令（System Prompt）

- [ ] System Prompt 写清楚规则（可直接贴到简历）：
  - 先从用户消息/会话记忆提取城市
  - 缺城市必须追问，不允许编造
  - 能用工具就用工具，拿到结构化数据后再输出自然语言
  - 输出要短、可执行（包含温度、是否带伞、穿搭建议）
- [ ] 输出格式约束（可选，但建议）：
  - content（给用户看的话术）
  - 可选 JSON 片段（便于前端结构化展示）

---

## 5. 可观测性（必做，简历加分）

### 5.1 traceId + toolTrace

- [ ] 每次请求生成 `traceId`（或复用 MDC）
- [ ] toolTrace 结构（示例）：
  - `[{ name, startTs, durationMs, inputSummary, outputSummary, status, error? }]`

### 5.2 指标（有时间就上）

- [ ] Micrometer 计数/直方图：
  - `llm.call.duration`
  - `qweather.call.duration`
  - `agent.tool.calls`（按 toolName 打 tag）

---

## 6. 评测与回归（别写太大，但要能说明“可验证”）

- [ ] 建一个“黄金对话集”（10~20 条）：
  - 缺城市 → 追问
  - 多轮“明天呢” → 复用城市
  - “北京和上海对比穿搭” → 可拆分为两次工具调用
  - 工具失败 → 友好降级
- [ ] 写一个最小的回归测试方式（任选其一）：
  - JUnit：对 Controller/Service 做集成测试（可 Mock 三方）
  - 或者：写一个脚本顺序请求并断言关键字（本地跑）

---

## 7. 演示层（前端可选）

不一定要 React/Vue；简历项目用“能演示”就行。

- [ ] 最小静态页（可放 resources/static）：
  - 聊天框 + 消息列表
  - 带上 sessionId（localStorage 保存）
  - 展示 content + 折叠展示 toolTrace（“开发者模式”）

---

## 8. README（周末最后 30min，强烈建议写）

- [ ] 项目一句话介绍 + 架构图（哪怕 ASCII）
- [ ] 启动方式（含环境变量）
- [ ] 2~3 条 curl 示例（/weather、/nimbus/chat、/nimbus/agent/chat）
- [ ] 你做了哪些工程化能力（memory/tool/trace/metrics）

---

## 9. 下周一我们要一起做的事（留给下周迭代）

- [ ] 代码整理：把“wrapper chat”和“agent chat”逻辑分层（controller/application/service/infrastructure）
- [ ] Redis memory（如果需要多实例/重启不丢）
- [ ] 用户画像（结构化 DB：PostgreSQL/MySQL；先别上向量库）
- [ ] 更严格的错误码与异常体系（统一 response）

