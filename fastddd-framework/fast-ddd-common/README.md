# FastDDD Common Module

FastDDD 通用工具模块，提供常用的工具类和基础功能。

## 模块概述

`fast-ddd-common` 是 FastDDD 框架的核心工具模块，提供了一系列常用的工具类，包括字符串处理、日期时间操作、随机数据生成、命名转换等功能。

## 主要功能

### 1. 字符串工具类 (StringUtils)

提供常用的字符串操作方法：

- 空值判断：`isEmpty()`, `isNotEmpty()`, `isBlank()`, `isNotBlank()`
- 字符串处理：`trim()`, `trimToEmpty()`, `trimToNull()`
- 默认值处理：`defaultIfNull()`, `defaultIfEmpty()`, `defaultIfBlank()`
- 字符串连接：`join()`
- 正则匹配：`matches()`
- 首字母大小写：`capitalize()`, `uncapitalize()`

### 2. 日期时间工具类 (DateUtils)

提供标准化的日期时间格式化和解析方法：

- 标准格式常量：`YYYY_MM_DD_HH_MM_SS`, `YYYY_MM_DD`, `HH_MM_SS` 等
- 格式化方法：`formatDefault()`, `format()`, `formatDate()`, `formatDateTime()`
- 解析方法：`parseDefault()`, `parseDateDefault()`, `parse()`, `parseDate()`
- 当前时间：`nowDefault()`, `nowDateTime()`, `nowDate()`
- 类型转换：`toLocalDateTime()`, `toDate()`
- 日期比较：`isSameDay()`

### 3. 随机数据生成工具类 (RandomUtils)

提供各种类型的随机数据生成方法：

- 随机字符串：`randomNumeric()`, `randomAlpha()`, `randomAlphaUpper()`, `randomString()`
- 随机数值：`randomInt()`, `randomLong()`, `randomDouble()`, `randomBoolean()`
- UUID生成：`randomUuid()`, `randomUuidWithHyphen()`
- 业务数据：`randomMobile()`, `randomEmail()`, `randomChineseName()`

### 4. 命名转换工具类 (NamingUtils)

提供数据库 snake_case 和 Java camelCase 之间的转换：

- `toCamelCase()`: snake_case → camelCase (例如：user_name → userName)
- `toSnakeCase()`: camelCase → snake_case (例如：userName → user_name)
- `pascalToCamelCase()`: PascalCase → camelCase (例如：UserName → userName)
- `camelToPascalCase()`: camelCase → PascalCase (例如：userName → UserName)
- `snakeToPascalCase()`: snake_case → PascalCase (例如：user_name → UserName)

## 使用方法

### Maven 依赖

```xml
<dependency>
    <groupId>com.gallop</groupId>
    <artifactId>fast-ddd-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 代码示例

#### 字符串工具类使用

```java
import com.gallop.fastddd.common.util.StringUtils;

// 空值判断
boolean isEmpty = StringUtils.isEmpty(""); // true
boolean isBlank = StringUtils.isBlank("  "); // true

// 默认值处理
String result = StringUtils.defaultIfBlank("", "default"); // "default"

// 字符串连接
String joined = StringUtils.join(",", "a", "b", "c"); // "a,b,c"

// 首字母大写
String capitalized = StringUtils.capitalize("hello"); // "Hello"
```

#### 日期时间工具类使用

```java
import com.gallop.fastddd.common.util.DateUtils;
import java.time.LocalDateTime;
import java.time.LocalDate;

// 格式化当前时间
String now = DateUtils.nowDefault(); // "2025-01-11 17:30:00"

// 自定义格式化
LocalDateTime dateTime = LocalDateTime.now();
String formatted = DateUtils.format(dateTime, "yyyy/MM/dd HH:mm"); // "2025/01/11 17:30"

// 解析日期字符串
LocalDateTime parsed = DateUtils.parseDefault("2025-01-11 17:30:00");

// 日期比较
LocalDate date1 = LocalDate.of(2025, 1, 11);
LocalDate date2 = LocalDate.of(2025, 1, 11);
boolean same = DateUtils.isSameDay(date1, date2); // true
```

#### 随机数据生成工具类使用

```java
import com.gallop.fastddd.common.util.RandomUtils;

// 生成随机数字字符串
String numeric = RandomUtils.randomNumeric(6); // "123456"

// 生成随机字母字符串
String alpha = RandomUtils.randomAlpha(8); // "abcdefgh"

// 生成随机整数
int randomInt = RandomUtils.randomInt(1, 100); // 1-100之间的随机数

// 生成UUID
String uuid = RandomUtils.randomUuid(); // "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"

// 生成随机手机号
String mobile = RandomUtils.randomMobile(); // "13812345678"

// 生成随机邮箱
String email = RandomUtils.randomEmail(); // "user123@example.com"

// 生成随机中文姓名
String chineseName = RandomUtils.randomChineseName(); // "张三"
```

#### 命名转换工具类使用

```java
import com.gallop.fastddd.common.util.NamingUtils;

// snake_case 转 camelCase
String camelCase = NamingUtils.toCamelCase("user_name"); // "userName"

// camelCase 转 snake_case
String snakeCase = NamingUtils.toSnakeCase("userName"); // "user_name"

// PascalCase 转 camelCase
String camelFromPascal = NamingUtils.pascalToCamelCase("UserName"); // "userName"

// camelCase 转 PascalCase
String pascalFromCamel = NamingUtils.camelToPascalCase("userName"); // "UserName"

// snake_case 转 PascalCase
String pascalFromSnake = NamingUtils.snakeToPascalCase("user_name"); // "UserName"
```

## 技术栈

- Java 17+
- Spring Boot 3.x
- Hutool 5.8.x
- JUnit 5
- Maven 3.6+

## 构建和测试

```bash
# 编译项目
mvn compile

# 运行测试
mvn test

# 打包项目
mvn package

# 生成文档
mvn javadoc:javadoc

# 生成源码包
mvn source:jar

# 生成文档包
mvn javadoc:jar
```

## 版本信息

- 当前版本：1.0.0-SNAPSHOT
- 最低 Java 版本：17
- 最低 Spring Boot 版本：3.0.0

## 许可证

本项目采用 Apache License 2.0 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request 来改进这个项目。

## 作者

- gallop

## 更新日志

### 1.0.0-SNAPSHOT (2025-01-11)

- 初始版本发布
- 实现 StringUtils 字符串工具类
- 实现 DateUtils 日期时间工具类
- 实现 RandomUtils 随机数据生成工具类
- 实现 NamingUtils 命名转换工具类
- 添加完整的单元测试
- 添加 Javadoc 文档