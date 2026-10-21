# 按上传日期导出

当前部署数据库为 **openGauss 7.0.0-RC3**，应用使用 PostgreSQL JDBC 驱动。连接、发布和验收命令见[部署说明](DEPLOYMENT.md)，接口参数见 [API 文档](API.md)。

## 使用

首页“下载范围”提供两种选择：

- 按上传日期选择：填写开始、结束日期，点击“按日期下载”。
- 2026年10月19日之前（历史数据）：隐藏日期输入框，点击“下载历史数据”。内部查询归档日期 2026-10-18，无需用户填写；需要先执行历史回填脚本。

日期下载接口：

`GET /api/db/export-by-upload-date?startDate=2026-10-01&endDate=2026-10-19`

日期均按北京时间，包含起止当天。缺少日期、日期格式错误、开始晚于结束返回 400。
无匹配数据时返回有效 ZIP，各业务表为 `[]`。全量接口 `/api/db/export-all` 保留。

例如，一个 10 月 1 日已经存在的会话在 10 月 20 日增量上传并成功写入新事件，选择 10 月 20 日就会包含它；下载的是该会话当前完整关联数据，也包含 10 月 1 日的旧事件。同一天多次上传仍只有一条汇总。仅尝试请求、上传失败或无变化重传，不等于当天发生有效上传。

## 数据口径

`session_upload_daily` 以 `(session_id, upload_date)` 为联合主键，保存首次及末次有效上传时间。
日期、时刻取服务器数据库时间（日期转换到 Asia/Shanghai），不取客户端创建时间。
同一天多次写入使用普通 UPDATE / INSERT，与业务写入在同一事务，失败一起回滚。
先更新当天记录，不存在则插入；并发首次插入发生唯一键冲突时，先回滚到插入前的保存点，
再更新另一事务已创建的记录。首次时间取较早值，末次时间取较晚值。
写入使用默认 READ COMMITTED 隔离级别；冲突后仍找不到记录则报错回滚，不返回虚假成功。
在线汇总不依赖 ON CONFLICT / ON DUPLICATE KEY UPDATE，避免 PostgreSQL 与 openGauss 7 RC/LTS 的语法差异。

记录路径：会话 `/upload`（新增会话、事件或实际投影变化）、任务 PUT、workspace/status PUT、
SSE 追加、快照保存、memory ingest、订阅新增/更新。现有服务器不支持 SSE/memory 批量接口，
客户端会降级到这些逐条接口。未来批量接口应复用这些服务方法。
仅 updated_at 或 last_upload_index 变化不算有效上传；相同业务内容的任务/快照重传不记录。
会话、任务和快照比较数据库实际写入结果，JSON 文本按结构比较，避免键顺序误判。

SSE 协议没有客户端事件唯一键，重复请求仍会插入新行，属于实际新增，无法识别为网络重试；
本功能不改变现有 SSE 语义。清空 SSE、软删除、共享用户/组织资料更新不作为会话上传。
单独 memory fold 的旧记录标记不作为上传；其中新 ingest 的记录正常记录。
一条汇总不表示整个客户端同步轮次已经完成：前面接口成功、后面接口失败，仍保留已成功的活动。

按日期筛选会话 ID 并去重，导出选中会话在导出快照中的完整关联数据，包含范围之外的历史记录。
用户及 cowork/权限只导出被选中会话引用的记录；无直接会话归属的 agent_templates 不包含。
全量导出仍导出原有 11 张业务表。上传汇总是服务器内部活动元数据，不放入导入用业务表文件。
ZIP 含 manifest.json（口径、日期、时区）和 export-summary.json（实际导出行数）。

## 部署与运行

新库及开启 `spring.sql.init.mode=always` 的实例由 schema.sql 建表。
生产关闭自动初始化时，先由数据库管理员执行 `docs/migrations/20261019-session-upload-daily.sql`，
再发布服务；新表存在之前不能启用这版写入服务。SQL 可重复执行，不回填、不修改历史业务行。
旧数据无法还原真实上传日期。按本项目约定，无上传记录的存量会话可通过一次性脚本
`docs/migrations/20261019-backfill-legacy-upload-date.sql` 统一归档到 **2026-10-18**，
表示“2026-10-19 之前”，不是实际上传日期。脚本不会随应用启动自动执行。

先确保汇总表存在，再在目标数据库手动执行整个回填脚本。它只选择首次执行时没有任何上传记录的
会话，不覆盖已有真实上传记录。`session_upload_legacy_baseline` 永久保存这批会话 ID、
归档日期及真实回填时间；重复执行使用原基线，不会把之后新增的无记录会话归到历史日期。
不要删除基线表。脚本会短暂阻塞汇总写入，建议在上传流量低时执行。
回填脚本在该表锁内使用 NOT EXISTS 跳过已有记录，不依赖 ON CONFLICT。
已经完成建表和历史回填的实例，本次汇总 SQL 兼容性修复只需更新服务，无需再次回填。

回填的 first_uploaded_at / last_uploaded_at 固定为北京时间 2026-10-18 23:59:59，
仅为归档占位时间；用基线表可区分真实上传与人为归档。查询 2026-10-18 即可包含这批历史会话。
会话后续再次有效上传，会另记当天的真实活动，原历史归档保留。
仅按会话归档，子表仍通过 session_id 关联导出，不按各行创建时间切分。
如果旧会话已经有上线后的真实上传记录，本脚本不会再为它补历史日期，因为无法从当前数据
可靠判断它在上线前是否已经上传。

继续按主键分批查询并流式写 ZIP，不在服务器缓存完整 ZIP，不把全部会话 ID 放到内存。
日期导出使用只读 REPEATABLE READ 事务，所有表对应同一个数据库快照；事务会占用连接，
长时间导出会延后旧版本回收，因此超大范围建议按天拆分，并按部署容量控制并发。
X-Accel-Buffering: no 请求代理不缓冲；代理仍需正确配置超时。网络中断仍可能使 ZIP 不完整。
接口沿用现有 /api/** 安全配置，本变更不扩大或收紧现有授权规则。

## 验证

`mvn -Dtest=UploadDateExportTest,UploadTrackingTest,UploadDailyJdbcTest,UploadDailyPostgresTest,ProjectionUpdaterUserPromptTest test`

H2 测试覆盖日期筛选、关联表、去重、分页、空结果、ZIP 结束目录、实际变更判断与事务回滚。
UploadTrackingTest 和 UploadDailyJdbcTest 执行实际汇总 SQL，仅为 H2 提供缺失的 statement_timestamp 时间函数。
并发测试强制两个事务同时发现当天无记录，验证唯一键冲突、保存点恢复以及冲突前后业务写入的提交。
使用独立 PostgreSQL/openGauss 测试库设置 `COWORK_TEST_DATABASE_URL`、`COWORK_TEST_DATABASE_USER`、
`COWORK_TEST_DATABASE_PASSWORD` 后，UploadDailyPostgresTest 验证真实数据库上的写入、并发、日期、回滚和历史回填。
兼容旧变量 `COWORK_TEST_POSTGRES_URL`、`COWORK_TEST_POSTGRES_USER`、`COWORK_TEST_POSTGRES_PASSWORD`。
该测试创建随机隔离 schema，结束后删除自己的 schema；未配置时跳过。

## 排查上传 HTTP 500

客户端的 HTTP 500 只能说明服务端失败，不能直接确定数据库报错原因。
在服务使用的数据库连接中执行 `SELECT version();`，确认完整版本为实际部署的 7.0.0-RC3，而不是只记录大版本“7”。
openGauss 7.0.0 LTS 新增 ON CONFLICT，7.0.0-RC3 的 INSERT 文档未提供该语法，不能只按大版本推断兼容性。
参考：[7.0.0 发行说明](https://docs.opengauss.org/zh/docs/7.0.0/release_notes/release_notes.html)、
[RC3 INSERT](https://docs.opengauss.org/en/docs/7.0.0-RC3/sql_reference/insert.html)。

同时检查 Java 服务端对应失败请求的异常栈，保留最底层 Caused by / SQLState / ERROR。
例如 Linux 中，进入 JAR 所在目录后查找日志：

```bash
find . -maxdepth 3 -type f \( -name "*.log" -o -name "nohup.out" \)
tail -n 500 nohup.out
```

将 nohup.out 换成实际日志路径；若由容器或 systemd 托管，应读取对应容器/服务的日志。
仅有 H2 测试通过不能视为部署数据库已验证，也不能证明所有 500 均由汇总 SQL 引起。

## 故障期间的数据与补传

如果某个日期的上传事务全部失败回滚，就不会有该日期的有效上传汇总，按该日期下载会是空数据。这不表示服务器原有业务数据已被清空，原有数据仍可通过全量或已归档历史范围获取。

服务修复后，需要客户端将失败数据重新上传。以实际成功补传日期记录活动，不会自动将其补为失败请求发生的日期。不要用事件创建时间推算真实上传日期，也不要用历史回填脚本补造故障期间的上传记录。

验收时结合客户端响应、Java 服务端异常、对应会话的业务行和 `session_upload_daily` 核对。客户端一轮同步含多个请求，部分请求成功、之后请求失败时，前面已提交的活动仍保留；不能仅凭整轮失败就判断所有子请求都未落库。
