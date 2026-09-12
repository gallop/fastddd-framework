# Maven Build Profiles Configuration

[English](MAVEN_PROFILES.md) | **简体中文**

本文档说明如何使用FastDDD框架中配置的Maven构建配置文件。

## 可用的构建配置文件

### 1. Development Profile (dev)
- **Profile ID**: `dev`
- **默认激活**: 是
- **用途**: 本地开发环境
- **特性**:
  - 使用H2内存数据库
  - 启用详细的调试日志
  - 启用Swagger文档
  - 启用Actuator监控
  - Redis连接到本地实例

### 2. Test Profile (test)
- **Profile ID**: `test`
- **默认激活**: 否
- **用途**: 测试环境
- **特性**:
  - 使用H2内存数据库
  - 减少日志输出
  - 禁用Swagger文档
  - 禁用Actuator监控
  - Redis连接到本地实例

### 3. Production Profile (prod)
- **Profile ID**: `prod`
- **默认激活**: 否
- **用途**: 生产环境
- **特性**:
  - 使用MySQL数据库
  - 最小化日志输出
  - 禁用Swagger文档
  - 启用Actuator监控
  - Redis连接到生产服务器

## 使用方法

### 1. 编译项目

```bash
# 使用默认的dev配置文件
mvn clean compile

# 显式指定dev配置文件
mvn clean compile -Pdev

# 使用test配置文件
mvn clean compile -Ptest

# 使用prod配置文件
mvn clean compile -Pprod
```

### 2. 打包项目

```bash
# 打包开发版本
mvn clean package -Pdev

# 打包测试版本
mvn clean package -Ptest

# 打包生产版本
mvn clean package -Pprod
```

### 3. 运行测试

```bash
# 使用test配置文件运行测试
mvn test -Ptest

# 使用dev配置文件运行测试
mvn test -Pdev
```

## 配置属性说明

### 数据库配置
- `database.url`: 数据库连接URL
- `database.username`: 数据库用户名
- `database.password`: 数据库密码

### Redis配置
- `redis.host`: Redis服务器地址
- `redis.port`: Redis服务器端口

### 日志配置
- `logging.level.root`: 根日志级别
- `logging.level.com.gallop.fastddd`: FastDDD框架日志级别

### 功能开关
- `enable.swagger`: 是否启用Swagger文档
- `enable.actuator`: 是否启用Actuator监控

## 资源过滤

项目配置了Maven资源过滤，会自动替换配置文件中的占位符：

- `@profile.active@` → 当前激活的配置文件名称
- `@database.url@` → 数据库连接URL
- `@logging.level.root@` → 根日志级别
- `@project.version@` → 项目版本号

## 示例配置文件

查看 `fastddd-studio-server/src/main/resources/application.yml` 文件，了解如何在Spring Boot配置中使用这些占位符。

## 注意事项

1. **生产环境安全**: 生产环境的数据库密码等敏感信息应通过环境变量或外部配置文件提供，而不是硬编码在pom.xml中。

2. **配置文件优先级**: Maven属性会被Spring Boot的配置文件覆盖，确保配置的一致性。

3. **资源过滤范围**: 只有`.yml`、`.yaml`和`.properties`文件会进行资源过滤，其他文件保持原样。

4. **构建缓存**: 切换配置文件后建议执行`mvn clean`清理缓存，确保配置正确应用。
