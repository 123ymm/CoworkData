# CoworkData

**CoworkData 是一个数据持久化服务。**

地端 / 云端客户端（如 IpMasterCowork、Host 服务）通过本服务对外暴露的 HTTP 接口，完成会话与事件数据的上传、查询、回放与入库持久化，底层存储为 PostgreSQL。

## 能做什么

- **增量上传**：客户端按水位上传会话事件，服务端幂等写入并更新上传进度
- **持久化入库**：领域事件写入 `events`，并维护会话投影 `sessions`、任务 `tasks`、快照等
- **查询与回放**：按用户列会话、查单条会话、回放全部事件、读 SSE 前端历史
- **软删**：会话逻辑删除（`delete_at`），关联事件数据保留

## 快速开始

1. 启动 PostgreSQL（例如 Docker 容器 `cowork-pg`，端口 `5432`）
2. 配置见 `src/main/resources/application.yml`
3. 运行 Spring Boot 应用后，默认地址：`http://localhost:8080`

```bash
# 连通性
curl "http://localhost:8080/api/test/hello"

# 按用户列会话
curl "http://localhost:8080/api/sessions?userId=zhangsan"
```

## 文档

完整接口说明（路由、请求体、调用示例）：

→ [docs/API.md](docs/API.md)

## 技术栈

- Java 21 / Spring Boot 3
- MyBatis-Plus
- PostgreSQL
