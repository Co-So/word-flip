# Docker 本地开发环境

WordFlip 本地三件套：**MySQL 8**、**Redis 7**、**MinIO**。Spring Boot 使用 `application-dev.yml` 连接；Flyway 在应用启动时自动迁移。

详见 [architecture.md §10](../docs/wordflip/architecture.md)。

## 服务一览

| 服务 | 镜像 | 宿主机端口 | 说明 |
|------|------|------------|------|
| MySQL | `mysql:8.0` | 3306 | 库 `wordflip`，utf8mb4_unicode_ci，UTC |
| Redis | `redis:7-alpine` | 6379 | AOF 持久化（可选密码） |
| MinIO API | `minio/minio` | 9000 | S3 兼容对象存储 |
| MinIO Console | `minio/minio` | 9001 | Web 管理界面 |

对象存储 Bucket：`wordflip`（`docker compose up` 时由 `minio-init` 自动创建）。

## 首次启动

```bash
cd docker

# 复制环境变量模板并编辑密码
cp .env.example .env          # Linux / macOS
copy .env.example .env        # Windows CMD
# PowerShell: Copy-Item .env.example .env

docker compose up -d
```

## 停止与清理

```bash
cd docker

# 停止容器（保留数据卷）
docker compose stop

# 停止并移除容器（仍保留命名卷）
docker compose down

# 停止并删除数据卷（清空 MySQL / Redis / MinIO 数据）
docker compose down -v
```

## 验证

```bash
cd docker

# 容器状态（mysql / redis / minio 应为 healthy 或 running；minio-init 为 Exited(0)）
docker compose ps

# MySQL：字符集与时区
docker compose exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW VARIABLES LIKE 'character_set_server'; SHOW VARIABLES LIKE 'collation_server'; SHOW VARIABLES LIKE 'time_zone';"

# Redis
docker compose exec redis redis-cli ping
# 若 .env 设置了 REDIS_PASSWORD：docker compose exec redis redis-cli -a "$REDIS_PASSWORD" ping

# MinIO API 健康检查
curl -f http://localhost:9000/minio/health/live

# 确认 bucket（需本机安装 mc，或使用 minio-init 日志）
docker compose logs minio-init
```

### MinIO Console

1. 浏览器打开 [http://localhost:9001](http://localhost:9001)
2. 使用 `.env` 中的 `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` 登录
3. 确认存在 bucket **`wordflip`**

若自动创建失败，可手动执行：

```bash
cd docker
sh minio/init-bucket.sh
```

或进入一次性容器：

```bash
docker compose run --rm minio-init
```

## 连接信息（Spring Boot dev 参考）

| 组件 | 地址 |
|------|------|
| MySQL | `jdbc:mysql://localhost:3306/wordflip?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC` |
| Redis | `localhost:6379` |
| MinIO API | `http://localhost:9000` |
| MinIO Bucket | `wordflip` |

## 数据持久化

命名卷（`docker compose down` 不会删除，除非加 `-v`）：

- `wordflip-mysql-data`
- `wordflip-redis-data`
- `wordflip-minio-data`

## 启动顺序（完整本地联调）

1. `docker compose up -d`（本目录）
2. `cd ../wordflip-server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
3. Android 模拟器 API：`http://10.0.2.2:8080/api/v1`
