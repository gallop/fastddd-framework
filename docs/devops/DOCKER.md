# FastDDD Framework Docker 部署指南

## 概述

本文档介绍如何使用 Docker 部署 FastDDD 框架，包括开发环境和生产环境的配置。

## 目录结构

```
fastDDD/
├── Dockerfile                    # 应用程序 Docker 镜像定义
├── .dockerignore                # Docker 构建忽略文件
├── docker-compose.yml           # 开发环境 Docker Compose 配置
├── docker-compose.prod.yml      # 生产环境 Docker Compose 配置
├── docker/                      # Docker 相关配置文件
│   ├── mysql/
│   │   ├── init/
│   │   │   └── 01-init-database.sql
│   │   └── conf/
│   │       └── my.cnf
│   ├── redis/
│   │   └── redis.conf
│   ├── nginx/
│   │   └── conf.d/
│   │       └── fastddd.conf
│   ├── prometheus/
│   │   └── prometheus.yml
│   ├── grafana/
│   │   └── provisioning/
│   │       ├── dashboards.yml
│   │       └── datasources.yml
│   └── filebeat/
│       └── filebeat.yml
└── scripts/
    ├── build-docker.sh          # Linux/macOS 构建脚本
    └── build-docker.ps1         # Windows PowerShell 构建脚本
```

## 快速开始

### 1. 开发环境部署

```bash
# 克隆项目
git clone <repository-url>
cd fastDDD

# 启动开发环境
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f fastddd-app
```

### 2. 生产环境部署

```bash
# 使用生产环境配置
docker-compose -f docker-compose.prod.yml up -d

# 或者使用构建脚本
./scripts/build-docker.sh -v 1.0.0 -p prod --push
```

## 服务说明

### 核心服务

| 服务名 | 端口 | 描述 |
|--------|------|------|
| fastddd-app | 8080 | FastDDD 应用程序主服务 |
| fastddd-app | 8081 | 管理端口（Actuator） |
| mysql | 3306 | MySQL 数据库 |
| redis | 6379 | Redis 缓存 |
| elasticsearch | 9200, 9300 | Elasticsearch 搜索引擎 |

### 可选服务

| 服务名 | 端口 | 描述 |
|--------|------|------|
| nginx | 80, 443 | 反向代理和负载均衡 |
| kibana | 5601 | Elasticsearch 可视化界面 |
| prometheus | 9090 | 监控数据收集 |
| grafana | 3000 | 监控数据可视化 |
| filebeat | 5066 | 日志收集代理 |

## 构建自定义镜像

### 使用构建脚本（推荐）

#### Linux/macOS

```bash
# 基本构建
./scripts/build-docker.sh

# 指定版本和配置
./scripts/build-docker.sh -v 1.0.0 -p prod

# 构建并推送到注册表
./scripts/build-docker.sh -v 1.0.0 -r registry.example.com --push

# 查看帮助
./scripts/build-docker.sh --help
```

#### Windows PowerShell

```powershell
# 基本构建
.\scripts\build-docker.ps1

# 指定版本和配置
.\scripts\build-docker.ps1 -Version "1.0.0" -Profile "prod"

# 构建并推送到注册表
.\scripts\build-docker.ps1 -Version "1.0.0" -Registry "registry.example.com" -Push

# 查看帮助
.\scripts\build-docker.ps1 -Help
```

### 手动构建

```bash
# 构建开发版本
docker build --build-arg MAVEN_PROFILE=dev -t fastddd/framework:dev .

# 构建生产版本
docker build --build-arg MAVEN_PROFILE=prod -t fastddd/framework:prod .

# 构建时指定版本信息
docker build \
  --build-arg MAVEN_PROFILE=prod \
  --build-arg VERSION=1.0.0 \
  --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
  --build-arg VCS_REF=$(git rev-parse --short HEAD) \
  -t fastddd/framework:1.0.0 .
```

## 环境配置

### 开发环境

开发环境使用 `docker-compose.yml` 配置，包含以下特性：

- 自动重新构建应用镜像
- 开发配置文件（dev profile）
- 数据库和缓存服务
- 可选的监控服务

### 生产环境

生产环境使用 `docker-compose.prod.yml` 配置，包含以下特性：

- 使用预构建的镜像
- 生产配置文件（prod profile）
- Nginx 反向代理
- 完整的监控和日志收集
- 资源限制和健康检查

## 环境变量配置

### 应用程序环境变量

```bash
# 数据库配置
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/fastddd
SPRING_DATASOURCE_USERNAME=fastddd
SPRING_DATASOURCE_PASSWORD=fastddd123

# Redis 配置
SPRING_REDIS_HOST=redis
SPRING_REDIS_PORT=6379

# Elasticsearch 配置
SPRING_ELASTICSEARCH_URIS=http://elasticsearch:9200

# 日志配置
LOGGING_LEVEL_ROOT=INFO
LOGGING_FILE_PATH=/var/log/fastddd

# JVM 配置
JAVA_OPTS=-Xmx512m -Xms256m
```

### 数据库环境变量

```bash
# MySQL
MYSQL_ROOT_PASSWORD=root123
MYSQL_DATABASE=fastddd
MYSQL_USER=fastddd
MYSQL_PASSWORD=fastddd123
```

### Redis 环境变量

```bash
# Redis（可选）
REDIS_PASSWORD=redis123
```

## 数据持久化

### 数据卷配置

```yaml
volumes:
  mysql_data:          # MySQL 数据
  redis_data:          # Redis 数据
  elasticsearch_data:  # Elasticsearch 数据
  grafana_data:        # Grafana 配置
  prometheus_data:     # Prometheus 数据
  app_logs:           # 应用日志
```

### 备份和恢复

```bash
# 备份 MySQL 数据
docker exec fastddd-mysql mysqldump -u fastddd -pfastddd123 fastddd > backup.sql

# 恢复 MySQL 数据
docker exec -i fastddd-mysql mysql -u fastddd -pfastddd123 fastddd < backup.sql

# 备份 Redis 数据
docker exec fastddd-redis redis-cli BGSAVE
docker cp fastddd-redis:/data/dump.rdb ./redis-backup.rdb
```

## 监控和日志

### 健康检查

```bash
# 应用程序健康检查
curl http://localhost:8080/actuator/health

# 数据库连接检查
curl http://localhost:8080/actuator/health/db

# Redis 连接检查
curl http://localhost:8080/actuator/health/redis
```

### 日志查看

```bash
# 查看应用日志
docker-compose logs -f fastddd-app

# 查看所有服务日志
docker-compose logs -f

# 查看特定时间段的日志
docker-compose logs --since="2024-01-01T00:00:00" fastddd-app
```

### 监控访问

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Kibana**: http://localhost:5601

## 性能调优

### JVM 调优

```bash
# 生产环境 JVM 参数
JAVA_OPTS="
  -Xmx2g
  -Xms1g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:+UseStringDeduplication
  -XX:+OptimizeStringConcat
  -XX:+UseCompressedOops
  -XX:+UseCompressedClassPointers
  -Djava.security.egd=file:/dev/./urandom
"
```

### 数据库调优

参考 `docker/mysql/conf/my.cnf` 中的配置：

- InnoDB 缓冲池大小
- 连接数限制
- 查询缓存配置
- 慢查询日志

### Redis 调优

参考 `docker/redis/redis.conf` 中的配置：

- 内存使用策略
- 持久化配置
- 网络优化

## 安全配置

### 网络安全

```bash
# 创建自定义网络
docker network create --driver bridge fastddd-network

# 限制服务间通信
# 在 docker-compose.yml 中配置网络策略
```

### 密码管理

```bash
# 使用 Docker Secrets（Swarm 模式）
echo "fastddd123" | docker secret create mysql_password -

# 使用环境变量文件
echo "MYSQL_PASSWORD=fastddd123" > .env
```

### SSL/TLS 配置

```bash
# 生成自签名证书（仅用于测试）
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout docker/nginx/ssl/key.pem \
  -out docker/nginx/ssl/cert.pem
```

## 故障排除

### 常见问题

1. **容器启动失败**
   ```bash
   # 查看容器日志
   docker-compose logs <service-name>
   
   # 检查容器状态
   docker-compose ps
   ```

2. **数据库连接失败**
   ```bash
   # 检查数据库服务状态
   docker-compose exec mysql mysql -u root -p -e "SHOW DATABASES;"
   
   # 检查网络连接
   docker-compose exec fastddd-app ping mysql
   ```

3. **内存不足**
   ```bash
   # 检查容器资源使用
   docker stats
   
   # 调整 JVM 内存设置
   # 修改 JAVA_OPTS 环境变量
   ```

4. **端口冲突**
   ```bash
   # 检查端口占用
   netstat -tulpn | grep :8080
   
   # 修改端口映射
   # 在 docker-compose.yml 中调整端口配置
   ```

### 调试模式

```bash
# 启用调试模式
docker-compose -f docker-compose.yml -f docker-compose.debug.yml up -d

# 连接调试端口
# 默认调试端口：5005
```

## 升级和维护

### 应用升级

```bash
# 拉取最新镜像
docker-compose pull

# 重新启动服务
docker-compose up -d

# 滚动更新（零停机）
docker-compose up -d --no-deps fastddd-app
```

### 清理和维护

```bash
# 清理未使用的镜像
docker image prune -f

# 清理未使用的卷
docker volume prune -f

# 清理未使用的网络
docker network prune -f

# 完整清理
docker system prune -af
```

## 最佳实践

1. **镜像管理**
   - 使用多阶段构建减小镜像大小
   - 定期更新基础镜像
   - 使用镜像标签进行版本管理

2. **配置管理**
   - 使用环境变量进行配置
   - 敏感信息使用 Docker Secrets
   - 配置文件外部化

3. **监控和日志**
   - 配置健康检查
   - 集中化日志收集
   - 设置监控告警

4. **安全**
   - 最小权限原则
   - 定期安全扫描
   - 网络隔离

5. **性能**
   - 合理设置资源限制
   - 使用缓存优化
   - 数据库连接池配置

## 参考资源

- [Docker 官方文档](https://docs.docker.com/)
- [Docker Compose 文档](https://docs.docker.com/compose/)
- [Spring Boot Docker 指南](https://spring.io/guides/gs/spring-boot-docker/)
- [FastDDD 框架文档](./README.md)