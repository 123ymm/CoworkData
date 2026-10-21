# 部署与运行：openGauss 7.0.0-RC3

## 1. 数据库连接

当前部署数据库为 **openGauss 7.0.0-RC3**。使用 `org.postgresql.Driver` 和 `jdbc:postgresql://主机:端口/数据库名`，不需要修改为 `jdbc:opengauss:`。

在应用所连接的数据库中执行以下只读查询：

```sql
SELECT version();
SELECT current_database(), current_user, current_schema();
SHOW search_path;
SHOW transaction_isolation;
```

上传写入使用数据库默认事务隔离级别，日汇总按 `READ COMMITTED` 设计。日期导出单独使用只读 `REPEATABLE READ`。

| 环境变量 | 说明 |
|---|---|
| `SPRING_DATASOURCE_URL` | 实际 openGauss JDBC 地址 |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | 数据库账号、密码 |
| `SPRING_SQL_INIT_MODE` | 默认 `always`；关闭启动建表用 `never` |
| `SERVER_PORT` | 默认 8080 |
| `COWORKDATA_EXPORT_BATCH_SIZE` | 默认每批查询 2000 行 |
| `SPRING_MVC_ASYNC_REQUEST_TIMEOUT` | 当前为 3600000 毫秒 |

数据库必须预先创建。默认启动建表需要 DDL 权限；关闭自动初始化后，由管理员预先准备表结构，应用账号仍需业务表及汇总表读写权限。

## 2. 构建与发布

使用 JDK 8，配置 `JAVA_HOME` 后执行：

```bash
java -version
mvn clean package -DskipTests
```

产物为 `target/cowork-data.jar`；上述命令跳过测试。将 JAR 上传到服务器实际部署目录，通过原有服务管理方式替换旧进程。手工运行的 Linux Bash 示例：

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://DB_HOST:5432/coworkdata'
export SPRING_DATASOURCE_USERNAME='DB_USER'
read -rsp '数据库密码: ' SPRING_DATASOURCE_PASSWORD
echo
export SPRING_DATASOURCE_PASSWORD
nohup java -jar cowork-data.jar >> cowork-data.log 2>&1 &
```

替换示例连接信息。示例假设旧进程已停止、当前目录就是部署目录。已有 systemd 或容器部署时，使用原有管理方式，不额外启动第二个进程。

## 3. 自动建表与手动迁移

| 文件 | 执行方式 | 作用 |
|---|---|---|
| `src/main/resources/schema.sql` | 打包进 JAR，默认随启动执行 | 创建当前业务表、汇总表和索引 |
| `docs/migrations/20261019-session-upload-daily.sql` | 手动执行 | 存量库补上传日汇总表，不回填 |
| `docs/migrations/20261019-backfill-legacy-upload-date.sql` | 手动执行全文 | 冻结历史基线并归档到 `2026-10-18` |

`docs/migrations/` 文件不会因上传 JAR 或启动服务自动执行。需要在服务器执行时单独上传 SQL 文件，也可在连接目标库的数据库工具中执行全文。

- 汇总表不存在且允许自动建表：默认启动配置会创建汇总表。
- 汇总表不存在且关闭自动初始化：管理员先执行汇总建表脚本，再发布服务。该脚本不代替新库完整 `schema.sql`。
- 尚未历史回填：确认“首次执行时所有无上传记录的会话均归为历史数据”的范围符合预期，再执行回填脚本。
- 已有表且历史回填完成：更新兼容性修复包即可，无需重建表、再次回填或删除任何表。

安装了 `gsql` 时可执行（替换连接参数及路径，按提示输入密码）：

```bash
gsql -h DB_HOST -p 5432 -d coworkdata -U DB_USER -v ON_ERROR_STOP=1 -f ./20261019-session-upload-daily.sql
gsql -h DB_HOST -p 5432 -d coworkdata -U DB_USER -v ON_ERROR_STOP=1 -f ./20261019-backfill-legacy-upload-date.sql
```

第二条不是每次发布的必做步骤。回填会短暂锁住汇总表，建议低流量时执行。保留 `session_upload_legacy_baseline`，用于固定首次回填范围和追溯来源。

`CREATE TABLE IF NOT EXISTS` 不会修复已存在表的全部列结构。`src/main/resources/db/migration/` 中的旧迁移由相应代码入口调用，不应当作启动自动迁移，也不等同于上传日汇总迁移。旧迁移需针对 RC3 单独审核，例如 V8 脚本仍有 `ON CONFLICT`，不要将旧迁移接口当作本次修复的常规发布步骤。

## 4. 发布后验收

```bash
curl --fail 'http://localhost:8080/api/test/hello'
```

此接口只检查服务响应。再让客户端上传一个可识别的新增测试会话或产生真实业务变化，确认上传成功；相同内容重传不一定新增当天汇总。

在数据库核对实际会话 ID：

```sql
SELECT session_id, upload_date, first_uploaded_at, last_uploaded_at
FROM session_upload_daily
WHERE session_id = '替换为刚刚上传的会话ID'
ORDER BY upload_date DESC;

SELECT upload_date, COUNT(*) AS session_count
FROM session_upload_daily
GROUP BY upload_date
ORDER BY upload_date DESC;
```

确认日期为北京时间，再通过首页按当天下载，检查 ZIP 解压和 `export-summary.json` 行数。

## 5. 自动化测试

上传与导出测试无需外部数据库：

```bash
mvn '-Dtest=UploadDateExportTest,UploadTrackingTest,UploadDailyJdbcTest,ProjectionUpdaterUserPromptTest' test
```

运行全套测试时，给应用启动测试提供 H2 数据源（Bash 示例）：

```bash
mvn test \
  '-Dspring.datasource.url=jdbc:h2:mem:alltests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE' \
  '-Dspring.datasource.driver-class-name=org.h2.Driver' \
  '-Dspring.datasource.username=sa' \
  '-Dspring.datasource.password='
```

实际 openGauss 验证使用独立测试库：

```bash
export COWORK_TEST_DATABASE_URL='jdbc:postgresql://TEST_DB_HOST:5432/coworkdata_test'
export COWORK_TEST_DATABASE_USER='TEST_DB_USER'
read -rsp '测试数据库密码: ' COWORK_TEST_DATABASE_PASSWORD
echo
export COWORK_TEST_DATABASE_PASSWORD
mvn -Dtest=UploadDailyPostgresTest test
```

该测试需要创建 schema 的权限，会创建随机 schema，验证并发、回滚、日期及历史回填后删除自己的 schema。未配置连接时跳过；类名中的 Postgres 为历史命名。H2 结果不能替代实际 openGauss 验收。

## 6. 常见问题

### 上传持续返回 500

数据库工具执行 `SELECT version();`，同时检查 Java 服务日志中的完整异常，重点是最底层 `Caused by`、`SQLState`、`ERROR`。客户端 HTTP 500 本身不能定位数据库根因。

手工启动示例对应日志命令：

```bash
tail -n 500 cowork-data.log
```

不确定位置时，在部署目录查找：

```bash
find . -maxdepth 3 -type f \( -name '*.log' -o -name 'nohup.out' \)
```

systemd 可用 `journalctl -u 实际服务名 -n 500 --no-pager`；容器可用 `docker logs --tail 500 实际容器名`。

RC3 不应使用 LTS 新增的 `ON CONFLICT` 写法。当前汇总已改用普通 SQL 和保存点；如仍报 500，应根据实际异常继续排查权限、schema、字段约束等，不能认定所有 500 原因相同。

### 日期下载为空

先查询对应日期的 `session_upload_daily`。它不是客户端尝试请求的日志：上传事务失败回滚，不会留下成功活动记录。原有数据仍可通过全量或已归档历史范围下载。修复后的成功补传按补传日期计入，不能自动还原故障日期的记录。

### ZIP 提示空、文件末端错误或数据错误

有效 ZIP 中数组为空与 ZIP 损坏是两种情况。后者应检查导出异常、连接中断和代理超时。当前导出直写 HTTP，不缓存完整 ZIP；响应开始后失败，HTTP 状态也未必能反映完整性。

大范围数据可按天拆分。服务异步超时当前一小时，代理和客户端也需匹配。不要仅凭下载完成或文件大小判断成功，应检查解压与导出统计。

相关文档：[API](API.md)、[按上传日期导出](UPLOAD_DATE_EXPORT.md)。
