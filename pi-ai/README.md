# pi-ai

Forma Pi AI：模型端口和厂商客户端。

## Scope

- `com.xmut.forma.pi.ai.message` — `Message`, `ContentPart`
- `com.xmut.forma.pi.ai.model` — `ModelProvider`, catalogs, decorators, ports
- `com.xmut.forma.pi.ai.tool` — `ToolCallEntry`, `ToolResult`
- `com.xmut.forma.pi.ai.provider` — OpenAI 兼容 HTTP；DashScope / DeepSeek 两套配置；按 catalog `provider` 路由

编排（会话 / 技能 / 工具环）在 `pi-agent`。本模块 **不** 依赖 pi-agent，也 **不** 引入第二套 HTTP 栈（仅 OkHttp 4.12.0）。DashScope / DeepSeek 各有独立 OkHttp 连接池（最多 8 条空闲、闲置 5 分钟）；不共用后勤层 `RestClient`。

`ModelProvider.stream`：厂商走 OpenAI 兼容 SSE（`"stream": true`，按行 `data:`）；包装器必须委托 `stream`，不能默认打回 `complete`。无覆盖时（Stub）仍一次 `complete` 再单包 delta。

环境变量：`DASHSCOPE_API_KEY`、`DEEPSEEK_API_KEY`。无 Key 时不注册真 `ModelProvider`（留给 pi-agent Stub）。

## Build

```bash
mvn -pl pi-ai -am test
```
