# 按上传日期导出

## 使用

首页“下载范围”提供两种选择：

- 按上传日期选择：填写开始、结束日期，点击“按日期下载”。
- 2026年10月19日之前（历史数据）：隐藏日期输入框，点击“下载历史数据”。内部查询归档日期 2026-10-18，无需用户填写；需要先执行历史回填脚本。

日期下载接口：

`GET /api/db/export-by-upload-date?startDate=2026-10-01&endDate=2026-10-19`

日期均按北京时间，包含起止当天。缺少日期、日期格式错误、开始晚于结束返回 400。
无匹配数据时返回有效 ZIP，各业务表为 `[]`。全量接口 `/api/db/export-all` 保留。

## 数据口径

`session_upload_daily` 以 `(session_id, upload_date)` 为联合主键，保存首次及末次有效上传时间。
日期、时刻取服务器数据库时间（日期转换到 Asia/Shanghai），不取客户端创建时间。
同一天多次写入使用 PostgreSQL 原子 UPSERT；与业务写入在同一事务，失败一起回滚。

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

`mvn -Dtest=UploadDateExportTest,UploadTrackingTest,UploadDailyPostgresTest,ProjectionUpdaterUserPromptTest test`

H2 测试覆盖日期筛选、关联表、去重、分页、空结果、ZIP 结束目录、实际变更判断与事务回滚。
H2 不支持生产 PostgreSQL UPSERT，UploadTrackingTest 对汇总写入使用测试替身。
使用独立 PostgreSQL 测试库设置 `COWORK_TEST_POSTGRES_URL`、`COWORK_TEST_POSTGRES_USER`、
`COWORK_TEST_POSTGRES_PASSWORD` 后，UploadDailyPostgresTest 验证真实 UPSERT、并发、日期和回滚。
该测试创建随机隔离 schema，结束后删除自己的 schema；未配置时跳过。
