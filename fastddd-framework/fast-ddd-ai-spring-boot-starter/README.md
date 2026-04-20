# FastDDD AI Spring Boot Starter

Spring Boot 自动配置启动器，提供 FastDDD 框架的 LLM 基础设施。

## 功能特性

- **LlmClient**: 统一的 LLM 客户端接口，支持 OpenAI 兼容 API（DeepSeek、Ollama 等）
- **流式调用**: 支持 SSE 流式响应，适合实时 AI 交互场景
- **Prompt 模板管理**: 外部化 Prompt 模板，支持占位符替换和配置覆盖
- **并发控制**: 内置 Semaphore 限流，防止 LLM API 过载

## 快速开始

### 添加依赖

```xml
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-ai-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 配置文件

在 `application.yml` 中添加配置：

```yaml
fast-ddd:
  ai:
    enabled: true
    llm:
      enabled: true
      provider: deepseek
      api-key: ${AI_API_KEY}
      base-url: https://api.deepseek.com/v1
      model: deepseek-chat
      temperature: 0.3
      max-tokens: 4096
      timeout: 60
      max-concurrency: 3
```

### 使用示例

```java
@Autowired
private LlmClient llmClient;

public void analyze() {
    List<ChatMessage> messages = List.of(
        new ChatMessage("system", "You are a DDD expert."),
        new ChatMessage("user", "Design tables for an e-commerce system.")
    );

    // 同步调用
    String response = llmClient.chat(messages, ChatOptions.defaults());

    // 流式调用
    llmClient.streamChat(messages, ChatOptions.defaults(),
        new StreamCallback() {
            @Override
            public void onChunk(String chunk) { System.out.print(chunk); }
            @Override
            public void onComplete() { System.out.println("\nDone"); }
            @Override
            public void onError(Exception e) { e.printStackTrace(); }
        }
    );
}
```

## 配置选项

### 主配置

| 配置项 | 默认值 | 说明 |
|-------|-------|------|
| `fast-ddd.ai.enabled` | `true` | 启用所有 AI 功能 |

### LLM 配置

| 配置项 | 默认值 | 说明 |
|-------|-------|------|
| `fast-ddd.ai.llm.enabled` | `false` | 启用 LLM 功能 |
| `fast-ddd.ai.llm.provider` | `openai` | LLM 提供商（openai/deepseek/ollama） |
| `fast-ddd.ai.llm.api-key` | - | API Key |
| `fast-ddd.ai.llm.base-url` | `https://api.openai.com/v1` | API 基础 URL |
| `fast-ddd.ai.llm.model` | `gpt-4o` | 模型名称 |
| `fast-ddd.ai.llm.temperature` | `0.3` | 生成温度（0.0-1.0） |
| `fast-ddd.ai.llm.max-tokens` | `4096` | 最大生成 Token 数 |
| `fast-ddd.ai.llm.timeout` | `60` | 请求超时（秒） |
| `fast-ddd.ai.llm.max-concurrency` | `3` | 最大并发请求数 |

## 许可证

Apache License 2.0
