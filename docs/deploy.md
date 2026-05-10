# 企业客服系统 — 部署说明

## 环境要求

| 组件 | 最低版本 |
|------|----------|
| JDK | 1.8+ |
| Maven | 3.6+ |
| MySQL | 5.7+ |
| Redis | 6.0+ |
| Nacos Server | 2.1+ |

## 本地启动步骤

### 1. 启动中间件

确保 MySQL、Redis、Nacos Server 已启动并可用。

### 2. 初始化数据库

执行 `docs/sql/init.sql` 初始化数据库表结构。

### 3. 启动服务

```bash
# 1. 编译整个项目
mvn clean package -DskipTests

# 2. 启动网关 (端口 8080)
cd chat-gateway
mvn spring-boot:run

# 3. 启动业务服务 (端口 8081)
cd chat-service
mvn spring-boot:run
```

### 4. 访问前端

浏览器打开 `http://localhost:8080` 进入客服系统登录页面。

## 配置说明

各模块配置文件位于 `src/main/resources/application.yml`：

- **Nacos 配置**: 服务注册与发现
- **Sentinel 配置**: 流量控制规则
- **MySQL 配置**: 数据库连接信息
- **Redis 配置**: 缓存连接信息
