这是一份为您整理好的**完整版** Markdown 文档。您可以直接点击右上角的“复制”按钮，将其无缝粘贴到您的 Notion、Obsidian 或团队知识库中。

***

# Spring Boot + Vue 3 企业级 SSE (Server-Sent Events) 最佳实践

SSE 是一种轻量级的单向实时通信技术（服务器向客户端推送）。在“大模型流式输出（打字机效果）”、“实时通知”、“系统监控大屏”等场景中，相比 WebSocket，SSE 具有**实现简单、基于标准 HTTP 协议、天然支持断线重连**等优势。

本文总结了基于 Spring Boot 和 Vue 3 的前后端完整实战指南，包含核心技术要点、JSON数据传输解析以及生产环境的踩坑总结。

---

## 一、 核心技术要点与避坑指南

### 1. 后端技术要点 (Spring Boot)
* **连接管理与内存泄漏**：必须使用线程安全的集合（如 `ConcurrentHashMap`）保存 `SseEmitter` 实例。同时**务必显式注册 `onCompletion`、`onTimeout` 和 `onError` 回调**清理连接，否则会导致服务端内存泄漏。
* **分布式部署问题**：SSE 连接是保存在单机内存中的长连接。如果后端多实例部署（负载均衡），推荐结合 **Redis Pub/Sub** 或 **MQ**。当产生业务消息时广播给所有节点，由持有该用户连接的节点执行真实推送。
* **心跳保活机制**：Nginx 等网关通常会切断长时间没有数据传输的 HTTP 空闲连接。后端建议设置定时任务，每隔 15~30 秒发送一个空注释（`comment`）作为心跳包。

### 2. 前端技术要点 (Vue 3)
* **不要使用原生 `EventSource`**：原生浏览器 API **不支持设置自定义请求头**（无法直接携带 JWT `Authorization` Token），在企业级应用中极不安全。
* **推荐方案**：采用微软开源的 `@microsoft/fetch-event-source`。它基于 Fetch API，支持自定义 Header、支持 POST 请求，且能精准控制重连策略。
* **JSON 容错解析**：服务端推送的字符串在前端需要反序列化为 JSON 对象。由于网络抖动或格式问题，**务必使用 `try-catch` 包裹 `JSON.parse()`**，防止单条异常数据导致整个页面/组件崩溃。
* **生命周期管理**：必须在 Vue 组件销毁时（`onBeforeUnmount`）通过 `AbortController` 手动中断请求，避免组件销毁后仍在后台默默监听甚至报错。

### 3. 运维/网关配置 (Nginx)
在 Nginx 代理 SSE 请求时，**必须关闭缓冲**，否则 Nginx 会等缓冲区满了才把数据发给前端，导致“实时流”变成“一块一块”的卡顿输出。
```nginx
location /api/sse/ {
    proxy_pass http://backend_server;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    # 核心配置：关闭代理缓冲
    proxy_buffering off;
    # 延长超时时间（如网关无心跳机制，需设长此值）
    proxy_read_timeout 3600s; 
}
```

---

## 二、 后端完整实现 (Spring Boot)

### 1. 业务实体类 (DTO)
用于演示 JSON 序列化。
```java
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SseMessageDto {
    private String msgId;
    private String action; // 例如: "update", "delete", "create"
    private Object content;
}
```

### 2. 连接管理服务 (SseService.java)
```java
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    // 保存 客户端标识 -> SseEmitter 的映射
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    /**
     * 建立连接
     */
    public SseEmitter connect(String clientId) {
        // 设置超时时间，0表示永不超时。此处设置 5 分钟，超时后前端会自动重连
        SseEmitter emitter = new SseEmitter(300_000L);
        emitterMap.put(clientId, emitter);

        // 注册各种结束状态的回调，清理集合防止内存泄漏
        Runnable removeCallback = () -> {
            log.info("SSE connection closed for client: {}", clientId);
            emitterMap.remove(clientId);
        };
        
        emitter.onCompletion(removeCallback);
        emitter.onTimeout(removeCallback);
        emitter.onError(e -> removeCallback.run());

        try {
            // 连接建立后立刻推送一条确认消息（可选）
            emitter.send(SseEmitter.event()
                    .name("connect_success")
                    .data("Connected successfully"));
        } catch (IOException e) {
            log.error("Error sending initial connect message", e);
            emitterMap.remove(clientId);
        }

        return emitter;
    }

    /**
     * 推送业务 JSON 数据
     */
    public void sendJsonMessage(String clientId, SseMessageDto messageDto) {
        SseEmitter emitter = emitterMap.get(clientId);
        if (emitter != null) {
            try {
                // SpringBoot 会自动通过 Jackson 将 DTO 转为 JSON 字符串
                emitter.send(SseEmitter.event()
                        .name("biz_message") // 前端根据此名称区分事件
                        .id(messageDto.getMsgId()) // 消息ID，帮助前端记录 Last-Event-ID
                        .data(messageDto, MediaType.APPLICATION_JSON)); 
            } catch (IOException e) {
                log.error("Failed to send message to client: {}", clientId, e);
                emitterMap.remove(clientId);
            }
        }
    }

    /**
     * 定时任务：每 20 秒发送一次心跳包，防止网关因为空闲切断连接
     * 注意：启动类上需要加上 @EnableScheduling
     */
    @Scheduled(fixedRate = 20000)
    public void sendHeartbeat() {
        emitterMap.forEach((clientId, emitter) -> {
            try {
                // 发送注释 (comment) 作为心跳，前端 @microsoft/fetch-event-source 默认会忽略 comment，不会触发 message 事件
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                emitterMap.remove(clientId);
            }
        });
    }
}
```

### 3. API 接口层 (SseController.java)
```java
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sse")
@CrossOrigin // 视项目情况配置跨域
public class SseController {

    private final SseService sseService;

    public SseController(SseService sseService) {
        this.sseService = sseService;
    }

    /**
     * 订阅接口：注意 produces 必须是 TEXT_EVENT_STREAM_VALUE
     */
    @GetMapping(path = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam("clientId") String clientId) {
        return sseService.connect(clientId);
    }
}
```

---

## 三、 前端完整实现 (Vue 3 + Composition API)

### 1. 安装核心依赖库
```bash
npm install @microsoft/fetch-event-source
```

### 2. 页面/组件代码 (SseComponent.vue)

```vue
<template>
  <div class="sse-demo">
    <h3>实时大盘数据监控</h3>
    <div class="status-bar" :class="connectionStatus === '已连接' ? 'success' : 'error'">
      连接状态: {{ connectionStatus }}
    </div>
    
    <div class="message-box">
      <div v-if="messages.length === 0" class="empty">暂无实时数据...</div>
      
      <!-- 渲染 JSON 解析后的数据 -->
      <div v-for="msg in messages" :key="msg.msgId" class="message-item">
        <span class="action">[{{ msg.action }}]</span>
        <span class="content">{{ msg.content }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { fetchEventSource } from '@microsoft/fetch-event-source';

// 响应式状态
const connectionStatus = ref('未连接');
const messages = ref([]);
const clientId = ref('user-' + Math.floor(Math.random() * 10000)); // 模拟当前用户ID

// 定义用于中断请求的控制器，防止组件销毁后仍在后台运行
let abortController = null;

// 建立 SSE 连接的方法
const setupSseConnection = async () => {
  // 1. 初始化 AbortController
  abortController = new AbortController();
  
  try {
    // 2. 发起 SSE 请求 (对应后端的 GET /api/sse/subscribe?clientId=xxx)
    await fetchEventSource(`http://localhost:8080/api/sse/subscribe?clientId=${clientId.value}`, {
      method: 'GET',
      // 这里可以携带鉴权 Token 等自定义 Header（原生 EventSource 无法实现）
      headers: {
        'Authorization': 'Bearer YOUR_JWT_TOKEN_HERE', 
        'Accept': 'text/event-stream'
      },
      signal: abortController.signal, // 绑定中断信号
      
      // 当连接成功打开时触发
      async onopen(response) {
        if (response.ok && response.headers.get('content-type')?.includes('text/event-stream')) {
          connectionStatus.value = '已连接';
          console.log('SSE 连接成功');
        } else {
          connectionStatus.value = '连接异常';
          throw new Error(`连接建立失败: ${response.status}`);
        }
      },
      
      // 当接收到服务端推送的消息时触发
      onmessage(event) {
        // 如果后端发送的是没有事件名的内容，默认事件名为 "message"
        // 只有后端发送的 event 名称匹配（或者前端不过滤全量接收），才会进入这里
        if (event.event === 'biz_message') {
          try {
            // 【关键踩坑点】必须用 try-catch 包裹，防止单条脏数据导致整个页面白屏
            const parsedData = JSON.parse(event.data);
            
            // 将新消息推入数组，展示到页面上
            messages.value.push({
              msgId: event.id, // 服务端推送的消息ID
              action: parsedData.action,
              content: parsedData.content
            });
            
          } catch (error) {
            console.error('JSON 解析失败，脏数据:', event.data, error);
          }
        } else if (event.event === 'connect_success') {
           console.log('收到后端握手确认:', event.data);
        }
      },
      
      // 连接正常关闭时触发
      onclose() {
        connectionStatus.value = '已断开';
        console.log('SSE 连接已正常关闭');
        // 如果后端主动断开，fetch-event-source 默认会尝试重连
        // 如果不想重连，可以在这里 throw new Error(); 并在 onerror 中处理
      },
      
      // 连接发生异常时触发
      onerror(err) {
        connectionStatus.value = '连接错误';
        console.error('SSE 连接发生错误:', err);
        // 返回数字（毫秒）即可控制重连间隔时间；如果不返回任何值或者抛出异常，则停止重连
        return 5000; // 发生错误后，延迟 5 秒重连
      }
    });
  } catch (err) {
    console.error('请求发起异常:', err);
  }
};

// 在组件挂载时建立连接
onMounted(() => {
  setupSseConnection();
});

// 【核心避坑】组件销毁前，必须手动中断当前正在进行的长连接
onBeforeUnmount(() => {
  if (abortController) {
    abortController.abort();
    abortController = null;
    console.log('组件销毁，已主动断开 SSE 连接');
  }
});
</script>

<style scoped>
/* 简单的美化样式 */
.sse-demo {
  max-width: 600px;
  margin: 20px auto;
  padding: 20px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-family: Arial, sans-serif;
  background-color: #f9f9f9;
}

.status-bar {
  padding: 8px 12px;
  border-radius: 4px;
  margin-bottom: 16px;
  font-weight: bold;
}

.success {
  background-color: #e6f4ea;
  color: #1e8e3e;
}

.error {
  background-color: #fce8e6;
  color: #d93025;
}

.message-box {
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 4px;
  height: 300px;
  overflow-y: auto;
  padding: 10px;
}

.empty {
  color: #999;
  text-align: center;
  margin-top: 100px;
}

.message-item {
  padding: 8px;
  border-bottom: 1px solid #eee;
  display: flex;
  gap: 10px;
}

.message-item .action {
  color: #1a73e8;
  font-weight: bold;
}

.message-item .content {
  color: #333;
}
</style>
```





这是一个**非常敏锐且专业**的问题！在实际开发中，这几乎是所有做 SSE（尤其是大模型打字机流式输出）开发者都会踩的一个天坑。

在本地开发阶段，Vite 底层使用的是 `http-proxy`。当后端返回 SSE 的流式数据时，代理层（或链路上的 Gzip 压缩组件）为了优化网络传输，会**默认将数据放在缓冲区，等攒够了一定大小才会发给前端**。这会导致你原本期待的“打字机一个字一个字蹦”的实时效果，变成“卡顿半天，然后突然一长串全出来”。

为了解决 Vite Proxy 缓冲，以及生产环境中 Nginx 的缓冲，我们有**“后端”和“前端”双管齐下**的完美解决方案。请将以下内容补充到你的 Markdown 文档中：

***

## 四、 核心补充：彻底解决反向代理（Vite Proxy / Nginx）缓冲问题

解决 SSE 缓冲问题的核心在于告诉所有的中间代理（包括本地的 Vite 和线上的 Nginx）：**不要缓存、不要压缩、收到数据立刻发走！**

### 1. 方案 A：在后端直接下发反缓冲 Header（最推荐，治标治本）

与其在每个环境（Vite / Nginx）单独配置，不如直接在 Spring Boot 响应头里把“防缓冲”规则写死。这是最优雅的做法。

修改 `SseController.java`，不要直接返回 `SseEmitter`，而是用 `ResponseEntity<SseEmitter>` 包装，并强行注入关键 Header：

```java
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
public class SseController {

    private final SseService sseService;

    public SseController(SseService sseService) {
        this.sseService = sseService;
    }

    @GetMapping(path = "/subscribe")
    public ResponseEntity<SseEmitter> subscribe(@RequestParam("clientId") String clientId) {
        SseEmitter emitter = sseService.connect(clientId);
        
        return ResponseEntity.ok()
                // 1. 明确声明内容类型为 SSE 的流
                .contentType(MediaType.TEXT_EVENT_STREAM)
                // 2. 【核心】禁用 HTTP 缓存，并且 "no-transform" 会明确告诉各种代理软件不要对内容进行压缩（比如 Gzip）
                .header("Cache-Control", "no-cache, no-transform")
                // 3. 【核心】告诉 Nginx 不要开启代理缓冲（此 Header 对 Nginx 有特效）
                .header("X-Accel-Buffering", "no")
                // 4. 保持长连接
                .header("Connection", "keep-alive")
                .body(emitter);
    }
}
```

### 2. 方案 B：在 Vite 配置文件中手动 Flush

如果由于某些原因你无法修改后端代码，或者 Vite 的某些本地中间件依然在作祟，你可以直接在 `vite.config.ts` 的 proxy 配置中拦截 SSE 请求，并强制清空 Node.js 的缓冲区。

修改 `vite.config.ts`（或 `.js`）：

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080', // 你的 Spring Boot 地址
        changeOrigin: true,
        
        // 【关键配置】解决 Vite 底层 http-proxy 的 SSE 缓冲问题
        configure: (proxy, options) => {
          proxy.on('proxyRes', (proxyRes, req, res) => {
            // 通过请求头 Accept 判断是否为 SSE 请求
            if (req.headers.accept && req.headers.accept.includes('text/event-stream')) {
              // 强制 Node.js 立即发送所有的 HTTP 响应头，而不是等待缓冲区填满
              // 这会让连接立刻进入真实的流式传输模式
              res.flushHeaders();
            }
          });
        }
      }
    }
  }
})
```

### 最佳实践总结：
强烈建议**直接采用方案 A（后端修改 ResponseEntity）**。因为方案 A 中的 `Cache-Control: no-transform` 和 `X-Accel-Buffering: no` 能够通杀 Vite Proxy 和线上的 Nginx，实现一份代码全环境生效，从根本上保证 SSE 的丝滑实时性。