# Maven Build Profiles Configuration

**English** | [简体中文](MAVEN_PROFILES.zh-CN.md)

This document explains how to use the Maven build profiles configured in the FastDDD framework.

## Available Build Profiles

### 1. Development Profile (dev)
- **Profile ID**: `dev`
- **Active by default**: Yes
- **Purpose**: local development environment
- **Features**:
  - Uses an in-memory H2 database
  - Enables verbose debug logging
  - Enables Swagger documentation
  - Enables Actuator monitoring
  - Redis connects to a local instance

### 2. Test Profile (test)
- **Profile ID**: `test`
- **Active by default**: No
- **Purpose**: test environment
- **Features**:
  - Uses an in-memory H2 database
  - Reduces log output
  - Disables Swagger documentation
  - Disables Actuator monitoring
  - Redis connects to a local instance

### 3. Production Profile (prod)
- **Profile ID**: `prod`
- **Active by default**: No
- **Purpose**: production environment
- **Features**:
  - Uses a MySQL database
  - Minimizes log output
  - Disables Swagger documentation
  - Enables Actuator monitoring
  - Redis connects to the production servers

## Usage

### 1. Compile the Project

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

### 2. Package the Project

```bash
# 打包开发版本
mvn clean package -Pdev

# 打包测试版本
mvn clean package -Ptest

# 打包生产版本
mvn clean package -Pprod
```

### 3. Run Tests

```bash
# 使用test配置文件运行测试
mvn test -Ptest

# 使用dev配置文件运行测试
mvn test -Pdev
```

## Configuration Properties

### Database Configuration
- `database.url`: database connection URL
- `database.username`: database username
- `database.password`: database password

### Redis Configuration
- `redis.host`: Redis server address
- `redis.port`: Redis server port

### Logging Configuration
- `logging.level.root`: root log level
- `logging.level.com.gallop.fastddd`: log level for the FastDDD framework

### Feature Switches
- `enable.swagger`: whether Swagger documentation is enabled
- `enable.actuator`: whether Actuator monitoring is enabled

## Resource Filtering

The project configures Maven resource filtering, which automatically replaces placeholders in configuration files:

- `@profile.active@` → the name of the currently active profile
- `@database.url@` → the database connection URL
- `@logging.level.root@` → the root log level
- `@project.version@` → the project version

## Example Configuration File

See the `fastddd-studio-server/src/main/resources/application.yml` file to learn how these placeholders are used in Spring Boot configuration.

## Notes

1. **Production environment security**: Sensitive information such as production database passwords should be provided through environment variables or external configuration files, not hardcoded in pom.xml.

2. **Configuration precedence**: Maven properties are overridden by Spring Boot configuration files, which keeps the configuration consistent.

3. **Resource filtering scope**: Only `.yml`, `.yaml`, and `.properties` files go through resource filtering; all other files are left as-is.

4. **Build cache**: After switching profiles, it is recommended to run `mvn clean` to clear cached output and make sure the new configuration is applied correctly.
