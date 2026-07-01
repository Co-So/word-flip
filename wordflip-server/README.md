# wordflip-server

Spring Boot 3 后端（MVP 脚手架）。

## 技术栈

- Java 21 + Spring Boot 3.3
- Spring Security + JWT（占位，P0 实现）
- Spring Data JPA + Flyway
- MySQL 8、Redis 7、MinIO

## 前置条件

1. 启动本地依赖（见 [../docker/README.md](../docker/README.md)）：

   ```bash
   cd ../docker
   docker compose up -d
   ```

2. 确保 MySQL 已创建库与用户（docker-compose 配置后自动创建）：
   - 库名：`wordflip`
   - 用户/密码：`wordflip` / `wordflip`
   - 端口：`3306`

## 运行

本机已安装 Maven 3.9+ 时：

```bash
cd wordflip-server
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

或使用 Maven Wrapper：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Windows PowerShell：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

打包（跳过测试）：

```bash
mvn -q -DskipTests package
java -jar target/wordflip-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

## 验证

| 端点 | 说明 |
|------|------|
| `GET http://localhost:8080/api/v1/health` | 健康检查（无需认证） |
| `http://localhost:8080/swagger-ui.html` | OpenAPI UI |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |

启动时 Flyway 自动执行 `V1__init_schema.sql`、`V2__seed_builtin_books.sql`。

## 配置

| 文件 | 说明 |
|------|------|
| `application.yml` | 通用配置（JPA validate、Flyway） |
| `application-dev.yml` | 本地 MySQL / Redis / MinIO |

## 包结构

```
com.wordflip/
├── config/          # Security、CORS、Redis、MinIO、OpenAPI
├── controller/      # REST 入口
├── service/         # 业务编排（待实现）
├── domain/          # JPA Entity（待实现）
├── repository/      # Spring Data JPA（待实现）
├── dto/             # 请求/响应 DTO
├── security/        # JWT（待实现）
├── storage/         # MinIO 封装
└── exception/       # 全局异常、ErrorResponse
```

## 参考

- API 契约：[../wordflip-api/openapi.yaml](../wordflip-api/openapi.yaml)
- 数据库：[../docs/wordflip/database-design.md](../docs/wordflip/database-design.md)
- 架构：[../docs/wordflip/architecture.md](../docs/wordflip/architecture.md)
- Agent 指令：[AGENTS.md](AGENTS.md)
