# CoworkData

CoworkData 是会话与轨迹数据的持久化服务。地端 / 云端客户端通过 HTTP 接口上传会话、事件、任务、快照、SSE 历史和记忆数据，服务端提供查询、回放、导入与下载。

**当前部署数据库：openGauss 7.0.0-RC3。** 项目使用 PostgreSQL JDBC 驱动和 `jdbc:postgresql://` 连接串连接 openGauss。驱动名称以及部分代码中的 `Postgres` 是技术实现或历史命名，不代表生产数据库是 PostgreSQL。

## 能做什么

- **增量上传**：客户端按水位上传会话事件，服务端幂等写入并更新上传进度
- **持久化入库**：领域事件写入 `events`，并维护会话投影 `sessions`、任务 `tasks`、快照等
- **查询与回放**：按用户列会话、查单条会话、回放全部事件、读 SSE 前端历史
- **软删**：会话逻辑删除（`delete_at`），关联事件数据保留
- **上传日汇总**：每个会话每天保存一条有效上传记录，按北京时间计算
- **下载**：全量、按上传日期、历史数据下载；分批查询并流式输出 ZIP，不在服务端预生成完整压缩包
- **单会话导入 / 导出**：提供 JSON bundle，供客户端处理迁移

## 快速开始

1. 准备 openGauss 7.0.0-RC3 数据库和应用账号，在数据库工具中执行 `SELECT version();` 核对完整版本。
2. 配置见 [application.yml](src/main/resources/application.yml)，用实际地址、账号和密码覆盖示例配置。
3. 使用 JDK 8 构建并运行，首页默认地址为 `http://localhost:8080/`。

Linux Bash 示例（密码交互输入，替换主机、端口、库名和账号）：

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://DB_HOST:5432/coworkdata'
export SPRING_DATASOURCE_USERNAME='DB_USER'
read -rsp '数据库密码: ' SPRING_DATASOURCE_PASSWORD
echo
export SPRING_DATASOURCE_PASSWORD
java -version
mvn clean package -DskipTests
java -jar target/cowork-data.jar
```

上述打包命令跳过测试。默认 `spring.sql.init.mode=always` 会在启动时执行 `schema.sql` 建表，账号需有相应权限，数据库本身需提前创建。存量库升级与手动迁移见[部署说明](docs/DEPLOYMENT.md)。

```bash
# 连通性
curl --fail "http://localhost:8080/api/test/hello"

# 按用户列会话
curl --fail "http://localhost:8080/api/sessions?userId=w30040833"
```

`/api/test/hello` 只验证服务能响应，不能证明数据库写入成功。当前 `/api/**` 和首页免认证，部署环境的访问控制需与此配置配套。

## 下载口径

| 方式 | 范围 |
|---|---|
| 全量下载 | 白名单中的 11 张业务表，不受上传日汇总限制 |
| 按上传日期下载 | 选择日期范围内有有效上传的会话，导出这些会话当前完整关联数据 |
| 历史数据 | 页面显示“2026年10月19日之前（历史数据）”，内部使用归档日期 `2026-10-18` |

日期范围包含起止当天，时区为 `Asia/Shanghai`。老会话当天增量上传产生实际新增或业务变化，也计入当天；相同内容重传、仅更新水位不计入。

日期下载不是按事件创建日期裁剪，也不是恢复当日快照。没有对应汇总时，返回有效 ZIP，业务数据数组为空；下载中断产生的损坏 ZIP 是另一类问题。

历史归档需手动回填，不能还原真实上传日期。上传事务失败回滚时不会留下当天汇总；之后补传成功按补传日期记录。

## 构建与验证

使用 JDK 8 执行上传与导出测试：

```bash
mvn '-Dtest=UploadDateExportTest,UploadTrackingTest,UploadDailyJdbcTest,ProjectionUpdaterUserPromptTest' test
```

真实数据库测试需配置独立测试库连接 `COWORK_TEST_DATABASE_URL`、`COWORK_TEST_DATABASE_USER`、`COWORK_TEST_DATABASE_PASSWORD`，再执行 `mvn -Dtest=UploadDailyPostgresTest test`。该测试创建随机 schema 并在结束后删除；未配置连接时跳过。H2 测试通过不等于实际 openGauss 已验收。

RC3 与 7.0.0 LTS 的 SQL 支持存在差异，不能仅按“openGauss 7”判断兼容性。当前上传日汇总使用普通 `UPDATE` / `INSERT` 和保存点，不依赖 `ON CONFLICT`。

## 文档与代码入口

| 入口 | 内容 |
|---|---|
| [API 文档](docs/API.md) | 路由、请求体、调用示例 |
| [部署说明](docs/DEPLOYMENT.md) | openGauss 配置、打包、迁移、验收与故障排查 |
| [按上传日期导出](docs/UPLOAD_DATE_EXPORT.md) | 有效上传口径、历史回填、下载范围 |
| [建表脚本](src/main/resources/schema.sql) | 当前表结构，默认随启动执行 |
| [上传日汇总建表脚本](docs/migrations/20261019-session-upload-daily.sql) | 存量库手动创建汇总表和索引 |
| [历史回填脚本](docs/migrations/20261019-backfill-legacy-upload-date.sql) | 冻结历史会话基线并归档 |
| [首页](src/main/resources/static/index.html) | 数据查看与下载入口 |

上传入口为 `SessionController` → `SessionSyncServiceImpl`，每日汇总由 `SessionUploadDailyService` 写入。下载入口为 `DbExportController` → `DbExportServiceImpl`。

## 技术栈

- Java 8 / Spring Boot 2.7
- MyBatis-Plus
- openGauss 7.0.0-RC3（使用 PostgreSQL JDBC 驱动）
