# 第三方组件许可清单（Third-Party Notices）

> FastDDD Framework 以 Apache License 2.0 发布（根 `LICENSE`）；本文件按已核实版本披露第三方组件的许可证、双/多许可选项与权威文本位置。
> 编制依据：依赖清单（RELEASE-03，242 组件）+ jar 内材料/发布 POM 逐项取证（2026-09-09）；覆盖层含原“未解析”项的全部补证结果。
> 快照日期：2026-09-09。

## 0. 分发形态与口径声明

- 本仓库发布的制品为本项目自有库 jar 与 POM（jar 内 `META-INF/` 附本仓库 `LICENSE`/`NOTICE`），**不包含任何第三方组件 jar 本体**；第三方组件由消费者按坐标自行从 Maven Central 等仓库解析。
- 分发形态定性：各第三方许可证中因“分发该组件本体”而生的实体义务（附许可证文本、保留声明、提供源码等），落点取决于**谁在分发该组件本体、以何种形态**；本仓库发布不含第三方本体，故上述义务不在本仓库分发物上触发。
- 本清单是披露实践，**不构成任何义务已履行（或无须履行）的声明**；组件 jar 内是否自带许可材料，也不自动等于该组件全部许可义务已满足——义务履行以实际分发形态对照各组件权威原文逐条判断。
- 任何人在本仓库制品之外另行分发第三方组件本体（聚合包、离线仓、镜像等）时，应以各组件权威许可原文为准自行判断并履行义务。
- 双/多许可组件：本清单列示全部合法选项；“标注选项”仅指本清单的披露口径，不替接收方行使其选择权。

## 1. 需单独注意的组件

| 组件 | 版本 | 许可证 | 权威文本位置 | 附注 |
|---|---|---|---|---|
| `com.mysql:mysql-connector-j` | 9.7.0 | GPL-2.0 WITH Universal FOSS Exception v1.0 | jar 根 `LICENSE`（GPLv2 全文 + uFOSS v1.0 全文，82896B） | 经 fast-ddd-crud runtime 传递引用；本仓库不分发该 jar 本体。与 Apache-2.0 软件组合运行/分发适用 uFOSS“Other FOSS”条款；对非 FOSS 组合的再分发以 uFOSS 末段与 GPL 原文为准 |
| `cn.hutool:hutool-all` | 5.8.34 | MulanPSL-2.0 | jar 内无许可证文件；官方全文随本仓库归档：`third-party-licenses/MulanPSL-2.0.txt` | fast-ddd-common 直依。再分发该组件本体时须向接收者提供许可证副本并保留版权/商标/专利/免责声明（MulanPSL-2.0 §4） |
| `ch.qos.logback:logback-classic` | 1.5.34 | EPL-2.0 OR LGPL-2.1-only（由接收方任选） | jar/pom 均无声明；官方双许可 pointer 随本仓库归档：`third-party-licenses/logback-LICENSE.txt` | 本清单双列、不替接收方选择；缺省指引 EPL-2.0（Spring Boot 生态惯例） |
| `ch.qos.logback:logback-core` | 1.5.34 | EPL-2.0 OR LGPL-2.1-only（由接收方任选） | 同 logback-classic（官方 pointer 归档） | 同 logback-classic |
| `com.github.jsqlparser:jsqlparser` | 5.1 | Apache-2.0 / LGPL-2.1（双许可） | 内嵌 pom 声明 | 本清单标注选项：Apache-2.0（与本项目主许可同构）；列示双选项供接收方自行分发时确认 |
| `jakarta.annotation:jakarta.annotation-api` | 2.1.1 | EPL-2.0 / GPL-2.0 with Classpath Exception（双许可） | jar 内 `LICENSE.md`（EPL-2.0 全文）+ `NOTICE.md` | 本清单标注选项：EPL-2.0 |
| `jakarta.xml.bind:jakarta.xml.bind-api` | 4.0.5 | EDL 1.0（= BSD-3-Clause，单许可） | jar 内 `LICENSE.md` + `NOTICE.md` | pom 声明 EDL 1.0；非 EPL/GPL 双许可 |
| `jakarta.activation:jakarta.activation-api` | 2.1.4 | EDL 1.0（= BSD-3-Clause，单许可） | jar 内 `LICENSE.md` + `NOTICE.md` |  |
| `org.aspectj:aspectjweaver` | 1.9.25.1 | 复合：EPL-2.0（主）+ 内嵌 ASM（BSD-3）+ 文档（Apache-1.1） | jar 内 `LICENSE-AspectJ.adoc` + `licenses/` 三份分许可全文 | pom 仅声明 EPL；以 jar 内实际携带文本为准，再分发须整体保留 |

## 2. 全量组件清单

共 242 项（含传递依赖）。许可证列以覆盖层核实结果为准；未特别标注者为其 pom 声明的常规宽松许可。

| 组件 | 版本 | 许可证 |
|---|---|---|
| `com.alibaba:druid-spring-boot-3-starter` | 1.2.28 | Apache-2.0 |
| `com.alibaba:druid` | 1.2.28 | Apache-2.0 |
| `com.alibaba:fastjson` | 1.2.69_noneautotype | Apache-2.0 |
| `com.baomidou:mybatis-plus-annotation` | 3.5.12 | Apache-2.0 |
| `com.baomidou:mybatis-plus-core` | 3.5.12 | Apache-2.0 |
| `com.baomidou:mybatis-plus-extension` | 3.5.12 | Apache-2.0 |
| `com.baomidou:mybatis-plus-generator` | 3.5.12 | Apache-2.0 |
| `com.baomidou:mybatis-plus-jsqlparser-common` | 3.5.12 | Apache-2.0 |
| `com.baomidou:mybatis-plus-jsqlparser` | 3.5.12 | Apache-2.0 |
| `com.baomidou:mybatis-plus-spring-boot-autoconfigure` | 3.5.12 | Apache-2.0 |
| `com.baomidou:mybatis-plus-spring-boot3-starter` | 3.5.12 | Apache-2.0 |
| `com.baomidou:mybatis-plus-spring` | 3.5.12 | Apache-2.0 |
| `com.baomidou:mybatis-plus` | 3.5.12 | Apache-2.0 |
| `com.ethlo.time:itu` | 1.14.0 | Apache-2.0 |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.21 | Apache-2.0 |
| `com.fasterxml.jackson.core:jackson-core` | 2.21.4 | Apache-2.0 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.21.4 | Apache-2.0 |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | 2.21.4 | Apache-2.0 |
| `com.fasterxml.jackson.datatype:jackson-datatype-jdk8` | 2.21.4 | Apache-2.0 |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.21.4 | Apache-2.0 |
| `com.fasterxml.jackson.module:jackson-module-jsonSchema` | 2.21.4 | Apache-2.0 |
| `com.fasterxml.jackson.module:jackson-module-parameter-names` | 2.21.4 | Apache-2.0 |
| `com.fasterxml:classmate` | 1.7.3 | Apache-2.0 |
| `com.github.ben-manes.caffeine:caffeine` | 3.2.4 | Apache-2.0 |
| `com.github.victools:jsonschema-generator` | 4.38.0 | Apache-2.0 |
| `com.github.victools:jsonschema-module-jackson` | 4.38.0 | Apache-2.0 |
| `com.github.victools:jsonschema-module-swagger-2` | 4.38.0 | Apache-2.0 |
| `com.github.xiaoymin:knife4j-core` | 4.5.0 | Apache-2.0 |
| `com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter` | 4.5.0 | Apache-2.0 |
| `com.github.xiaoymin:knife4j-openapi3-ui` | 4.5.0 | Apache-2.0 |
| `com.google.code.findbugs:jsr305` | 3.0.2 | Apache-2.0 |
| `com.google.code.gson:gson` | 2.13.2 | Apache-2.0 |
| `com.google.errorprone:error_prone_annotations` | 2.41.0 | Apache-2.0 |
| `com.google.errorprone:error_prone_annotations` | 2.49.0 | Apache-2.0 |
| `com.google.guava:failureaccess` | 1.0.1 | Apache-2.0 |
| `com.google.guava:guava` | 31.0.1-jre | Apache-2.0 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava | Apache-2.0 |
| `com.google.j2objc:j2objc-annotations` | 1.3 | Apache-2.0 |
| `com.googlecode.concurrentlinkedhashmap:concurrentlinkedhashmap-lru` | 1.4.2 | Apache-2.0 |
| `com.networknt:json-schema-validator` | 2.0.0 | Apache-2.0 |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Apache-2.0 |
| `com.squareup.okio:okio-jvm` | 3.6.0 | Apache-2.0 |
| `com.squareup.okio:okio` | 3.6.0 | Apache-2.0 |
| `com.zaxxer:HikariCP` | 6.3.3 | Apache-2.0 |
| `commons-beanutils:commons-beanutils` | 1.9.4 | Apache-2.0 |
| `commons-cli:commons-cli` | 1.4 | Apache-2.0 |
| `commons-codec:commons-codec` | 1.18.0 | Apache-2.0 |
| `commons-collections:commons-collections` | 3.2.2 | Apache-2.0 |
| `commons-digester:commons-digester` | 2.1 | Apache-2.0 |
| `commons-logging:commons-logging` | 1.2 | Apache-2.0 |
| `commons-validator:commons-validator` | 1.7 | Apache-2.0 |
| `de.codecentric:spring-boot-admin-client` | 3.1.8 | Apache-2.0 |
| `de.codecentric:spring-boot-admin-starter-client` | 3.1.8 | Apache-2.0 |
| `io.jsonwebtoken:jjwt-api` | 0.11.5 | Apache-2.0 |
| `io.jsonwebtoken:jjwt-impl` | 0.11.5 | Apache-2.0 |
| `io.jsonwebtoken:jjwt-jackson` | 0.11.5 | Apache-2.0 |
| `io.micrometer:context-propagation` | 1.1.4 | Apache-2.0 |
| `io.micrometer:micrometer-commons` | 1.15.12 | Apache-2.0 |
| `io.micrometer:micrometer-core` | 1.15.12 | Apache-2.0 |
| `io.micrometer:micrometer-jakarta9` | 1.15.12 | Apache-2.0 |
| `io.micrometer:micrometer-observation` | 1.15.12 | Apache-2.0 |
| `io.micrometer:micrometer-registry-prometheus` | 1.15.12 | Apache-2.0 |
| `io.micrometer:micrometer-tracing-bridge-brave` | 1.5.12 | Apache-2.0 |
| `io.micrometer:micrometer-tracing` | 1.5.12 | Apache-2.0 |
| `io.netty:netty-all` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-buffer` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec-dns` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec-haproxy` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec-http2` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec-http` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec-memcache` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec-mqtt` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec-redis` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec-smtp` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec-socks` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec-stomp` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec-xml` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-codec` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-common` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-handler-proxy` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-handler-ssl-ocsp` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-handler` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-resolver-dns-classes-macos` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-resolver-dns-native-macos` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-resolver-dns-native-macos` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-resolver-dns` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-resolver` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport-classes-epoll` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport-classes-kqueue` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport-native-epoll` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport-native-epoll` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport-native-epoll` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport-native-kqueue` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport-native-kqueue` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport-native-unix-common` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport-rxtx` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport-sctp` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport-udt` | 4.1.135.Final | Apache-2.0 |
| `io.netty:netty-transport` | 4.1.135.Final | Apache-2.0 |
| `io.projectreactor:reactor-core` | 3.7.19 | Apache-2.0 |
| `io.prometheus:prometheus-metrics-config` | 1.3.10 | Apache-2.0 |
| `io.prometheus:prometheus-metrics-core` | 1.3.10 | Apache-2.0 |
| `io.prometheus:prometheus-metrics-exposition-formats` | 1.3.10 | Apache-2.0 |
| `io.prometheus:prometheus-metrics-exposition-textformats` | 1.3.10 | Apache-2.0 |
| `io.prometheus:prometheus-metrics-model` | 1.3.10 | Apache-2.0 |
| `io.swagger.core.v3:swagger-annotations-jakarta` | 2.2.38 | Apache-2.0 |
| `io.swagger.core.v3:swagger-annotations-jakarta` | 2.2.47 | Apache-2.0 |
| `io.swagger.core.v3:swagger-core-jakarta` | 2.2.47 | Apache-2.0 |
| `io.swagger.core.v3:swagger-models-jakarta` | 2.2.47 | Apache-2.0 |
| `io.zipkin.aws:brave-propagation-aws` | 1.3.0 | Apache-2.0 |
| `io.zipkin.brave:brave-context-slf4j` | 6.1.0 | Apache-2.0 |
| `io.zipkin.brave:brave-instrumentation-http` | 6.1.0 | Apache-2.0 |
| `io.zipkin.brave:brave` | 6.1.0 | Apache-2.0 |
| `io.zipkin.contrib.brave-propagation-w3c:brave-propagation-tracecontext` | 0.2.0 | Apache-2.0 |
| `io.zipkin.reporter2:zipkin-reporter-brave` | 3.5.3 | Apache-2.0 |
| `io.zipkin.reporter2:zipkin-reporter` | 3.5.3 | Apache-2.0 |
| `jakarta.validation:jakarta.validation-api` | 3.0.2 | Apache-2.0 |
| `javax.validation:validation-api` | 1.1.0.Final | Apache-2.0 |
| `org.apache.commons:commons-lang3` | 3.17.0 | Apache-2.0 |
| `org.apache.logging.log4j:log4j-api` | 2.24.3 | Apache-2.0 |
| `org.apache.logging.log4j:log4j-to-slf4j` | 2.24.3 | Apache-2.0 |
| `org.apache.rocketmq:rocketmq-acl` | 5.0.0 | Apache-2.0 |
| `org.apache.rocketmq:rocketmq-client` | 5.0.0 | Apache-2.0 |
| `org.apache.rocketmq:rocketmq-common` | 5.0.0 | Apache-2.0 |
| `org.apache.rocketmq:rocketmq-logging` | 5.0.0 | Apache-2.0 |
| `org.apache.rocketmq:rocketmq-proto` | 2.0.0 | Apache-2.0 |
| `org.apache.rocketmq:rocketmq-remoting` | 5.0.0 | Apache-2.0 |
| `org.apache.rocketmq:rocketmq-spring-boot-starter` | 2.2.3 | Apache-2.0 |
| `org.apache.rocketmq:rocketmq-spring-boot` | 2.2.3 | Apache-2.0 |
| `org.apache.rocketmq:rocketmq-srvutil` | 5.0.0 | Apache-2.0 |
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.1.55 | Apache-2.0 |
| `org.apache.tomcat.embed:tomcat-embed-el` | 10.1.55 | Apache-2.0 |
| `org.apache.tomcat.embed:tomcat-embed-websocket` | 10.1.55 | Apache-2.0 |
| `org.awaitility:awaitility` | 4.2.2 | Apache-2.0 |
| `org.hibernate.validator:hibernate-validator` | 8.0.3.Final | Apache-2.0 |
| `org.jboss.logging:jboss-logging` | 3.6.3.Final | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-common` | 1.9.25 | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7` | 1.9.25 | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8` | 1.9.25 | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 1.9.25 | Apache-2.0 |
| `org.jetbrains:annotations` | 13.0 | Apache-2.0 |
| `org.jspecify:jspecify` | 1.0.0 | Apache-2.0 |
| `org.lz4:lz4-java` | 1.8.0 | Apache-2.0 |
| `org.mapstruct:mapstruct` | 1.5.5.Final | Apache-2.0 |
| `org.mybatis:mybatis-spring` | 3.0.4 | Apache-2.0 |
| `org.mybatis:mybatis` | 3.5.13 | Apache-2.0 |
| `org.mybatis:mybatis` | 3.5.19 | Apache-2.0 |
| `org.springdoc:springdoc-openapi-starter-common` | 2.8.17 | Apache-2.0 |
| `org.springdoc:springdoc-openapi-starter-webmvc-api` | 2.8.17 | Apache-2.0 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.8.17 | Apache-2.0 |
| `org.springframework.ai:spring-ai-autoconfigure-model-chat-client` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-autoconfigure-model-chat-memory` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-autoconfigure-model-chat-observation` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-autoconfigure-model-embedding-observation` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-autoconfigure-model-image-observation` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-autoconfigure-model-openai` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-autoconfigure-model-tool` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-autoconfigure-retry` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-client-chat` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-commons` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-model` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-openai` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-retry` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-starter-model-openai` | 1.1.8 | Apache-2.0 |
| `org.springframework.ai:spring-ai-template-st` | 1.1.8 | Apache-2.0 |
| `org.springframework.boot:spring-boot-actuator-autoconfigure` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-actuator` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-autoconfigure` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-configuration-processor` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-actuator` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-aop` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-cache` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-data-redis` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-jdbc` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-json` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-logging` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-security` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-tomcat` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-validation` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-web` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter` | 3.5.16 | Apache-2.0 |
| `org.springframework.boot:spring-boot` | 3.5.16 | Apache-2.0 |
| `org.springframework.data:spring-data-commons` | 3.5.13 | Apache-2.0 |
| `org.springframework.data:spring-data-keyvalue` | 3.5.13 | Apache-2.0 |
| `org.springframework.data:spring-data-redis` | 3.5.13 | Apache-2.0 |
| `org.springframework.retry:spring-retry` | 2.0.13 | Apache-2.0 |
| `org.springframework.security:spring-security-config` | 6.5.11 | Apache-2.0 |
| `org.springframework.security:spring-security-core` | 6.5.11 | Apache-2.0 |
| `org.springframework.security:spring-security-crypto` | 6.5.11 | Apache-2.0 |
| `org.springframework.security:spring-security-web` | 6.5.11 | Apache-2.0 |
| `org.springframework:spring-aop` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-beans` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-context-support` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-context` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-core` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-expression` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-jcl` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-jdbc` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-messaging` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-oxm` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-tx` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-web` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-webflux` | 6.2.19 | Apache-2.0 |
| `org.springframework:spring-webmvc` | 6.2.19 | Apache-2.0 |
| `org.webjars:swagger-ui` | 5.32.2 | Apache-2.0 |
| `org.yaml:snakeyaml` | 2.4 | Apache-2.0 |
| `com.auth0:java-jwt` | 4.5.0 | MIT |
| `com.knuddels:jtokkit` | 1.1.0 | MIT |
| `com.meilisearch.sdk:meilisearch-java` | 0.16.1 | MIT |
| `io.lettuce:lettuce-core` | 6.6.0.RELEASE | MIT |
| `io.modelcontextprotocol.sdk:mcp-core` | 0.18.3 | MIT |
| `io.modelcontextprotocol.sdk:mcp-json-jackson2` | 0.18.3 | MIT |
| `org.checkerframework:checker-qual` | 3.12.0 | MIT |
| `org.projectlombok:lombok` | 1.18.46 | MIT |
| `org.reactivestreams:reactive-streams` | 1.0.4 | MIT |
| `org.slf4j:jul-to-slf4j` | 2.0.18 | MIT |
| `org.slf4j:slf4j-api` | 2.0.18 | MIT |
| `org.webjars:webjars-locator-lite` | 1.1.3 | MIT |
| `redis.clients.authentication:redis-authx-core` | 0.1.1-beta2 | MIT |
| `com.github.luben:zstd-jni` | 1.5.2-2 | BSD |
| `com.google.protobuf:protobuf-java-util` | 3.20.1 | BSD |
| `com.google.protobuf:protobuf-java` | 3.20.1 | BSD |
| `org.antlr:antlr-runtime` | 3.5.3 | BSD |
| `org.antlr:antlr4-runtime` | 4.13.1 | BSD |
| `org.hamcrest:hamcrest` | 3.0 | BSD |
| `ch.qos.logback:logback-classic` | 1.5.34 | EPL-2.0 OR LGPL-2.1-only（由接收方任选） |
| `ch.qos.logback:logback-core` | 1.5.34 | EPL-2.0 OR LGPL-2.1-only（由接收方任选） |
| `org.json:json` | 20250517 | Public Domain |
| `org.latencyutils:LatencyUtils` | 2.0.3 | Public Domain |
| `com.github.jsqlparser:jsqlparser` | 5.1 | Apache-2.0 / LGPL-2.1（双许可） |
| `org.hdrhistogram:HdrHistogram` | 2.2.2 | BSD | Public Domain |
| `jakarta.xml.bind:jakarta.xml.bind-api` | 4.0.5 | EDL 1.0（= BSD-3-Clause，单许可） |
| `org.aspectj:aspectjweaver` | 1.9.25.1 | 复合：EPL-2.0（主）+ 内嵌 ASM（BSD-3）+ 文档（Apache-1.1） |
| `com.mysql:mysql-connector-j` | 9.7.0 | GPL-2.0 WITH Universal FOSS Exception v1.0 |
| `cn.hutool:hutool-all` | 5.8.34 | MulanPSL-2.0 |
| `jakarta.activation:jakarta.activation-api` | 2.1.4 | EDL 1.0（= BSD-3-Clause，单许可） |
| `jakarta.annotation:jakarta.annotation-api` | 2.1.1 | EPL-2.0 / GPL-2.0 with Classpath Exception（双许可） |
| `org.antlr:ST4` | 4.3.4 | BSD（pom 声明） |
| `aopalliance:aopalliance` | 1.0 | Public Domain（发布 POM 声明） |
| `io.prometheus:prometheus-metrics-tracer-common` | 1.3.10 | Apache-2.0（同仓（client_java）官方 LICENSE） |
| `net.logstash.logback:logstash-logback-encoder` | 7.4 | Apache-2.0 / MIT（双许可）（内嵌 pom 声明） |
| `org.apache.tomcat:annotations-api` | 6.0.53 | Apache-2.0（jar 内 META-INF/LICENSE + NOTICE） |

## 3. 随本仓库归档的许可证全文

| 文件 | 对应组件 | 归档原因 |
|---|---|---|
| `third-party-licenses/MulanPSL-2.0.txt` | hutool-all 5.8.34 | 该组件 jar 内不携带许可证文本，且 MulanPSL-2.0 §4 要求再分发时提供许可证副本——归档官方原文（coscl）供取用 |
| `third-party-licenses/logback-LICENSE.txt` | logback-core/classic 1.5.34 | 双许可信息不在 jar/pom 内，官方 pointer（“由接收方任选”）是选项依据——归档官方原文（QOS.ch）供取用 |

> 其余组件的许可全文以其 jar 内携带文本或官方发布地址为权威来源，本仓库不重复归档。
> 组件级依赖图与漏洞对应关系见随发布提供的 SBOM（CycloneDX 1.5）。

