# wordflip-server — Agent 指令

> 父级：[../AGENTS.md](../AGENTS.md)

## 范围

Spring Boot 3 单体后端：REST API、业务规则唯一真相、MySQL + Redis + MinIO。

## 包结构

```
com.wordflip/
├── config/          # Security、Redis、MinIO、CORS
├── controller/      # 对齐 openapi paths
├── service/         # AuthService、BookService、GroupService、ReviewService、QuizService…
├── domain/          # JPA Entity
├── repository/
├── dto/
├── security/        # JWT
├── storage/         # MinIO
└── exception/       # ErrorResponse + @ControllerAdvice
```

## 硬性规则

- **中文注释**：类/方法 JavaDoc、Service 业务分支、Flyway 表/索引说明使用**简体中文**（见 [coding-standards.md](../docs/wordflip/coding-standards.md)）  
- **掌握度**：仅 `ReviewService.applyQuizResult(userId, wordKey, correct)`；由 `QuizService` 在 `POST .../answer` 调用  
- **分组**：`GroupService.appendGroupsForNewWords` 在 `PUT /settings` 后执行；只 INSERT，不 DELETE 旧 groups  
- **Flyway**：`src/main/resources/db/migration/V{n}__*.sql`；JPA `ddl-auto: validate`  
- **Controller**：无 SRS/判题/append 逻辑；事务在 Service  
- **缓存**：写 mastery/review 后删 Redis `today:{userId}:{date}`  

## 关键 Service 映射

| Service | 职责 |
|---------|------|
| `ReviewService` | SRS、`GET /today` 计数、`applyQuizResult` |
| `QuizService` | 出题、判题 `trim + equalsIgnoreCase` |
| `BookImportService` | preview Redis 15min + confirm 入库 |
| `ImageService` | WebP → MinIO `card-images/{userId}/{wordKey}.webp` |

## 命令

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw test
```

依赖：`../docker/docker-compose.yml` 先启动。

## 参考

- [../wordflip-api/openapi.yaml](../wordflip-api/openapi.yaml)
- [../docs/wordflip/database-design.md](../docs/wordflip/database-design.md)
- [../TASK.md](../TASK.md) §S、§P0-B～P4-B
