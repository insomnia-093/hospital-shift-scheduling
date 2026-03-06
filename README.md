# 🏥 医院排班系统（Hospital Scheduling Agent）

<div align="center">


**一个基于 Spring Boot + Vue 3 的智能医院排班管理系统**

![Java 17](https://img.shields.io/badge/Java-17+-green?style=flat)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.1-green?style=flat)
![Vue 3](https://img.shields.io/badge/Vue-3-brightgreen?style=flat)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-blue?style=flat)

[快速开始](#快速开始) • [功能特性](#功能特性) • [技术栈](#技术栈) • [Docker 部署](#docker-部署)

</div>

---

## 📋 项目简介

**医院排班系统** 是一个为医院管理部门设计的智能排班管理平台，支持班次管理、员工调度、实时沟通和智能体协作。系统采用前后端分离架构，提供完整的权限控制、数据持久化和实时通信能力。

### ✨ 核心特性

- **账号管理**：支持登录、注册、JWT 无状态认证
- **权限控制**：5 级角色系统（ADMIN/COORDINATOR/DOCTOR/NURSE/AGENT）
- **智能排班**：班次创建、员工指派、状态跟踪、冲突检测
- **实时通信**：基于 WebSocket 的多频道消息系统
- **智能体协作**：任务创建、状态跟踪、聊天历史持久化
- **管理后台**：用户密码重置、班次详情修改、数据统计
- **生产就绪**：异常处理、日志记录、监控告警
- **Docker 部署**：一键启动、多服务容器编排、云部署支持

---

## 🚀 快速开始

### 前置条件

| 要求           | 版本 | 说明       |
| -------------- | ---- | ---------- |
| **JDK**        | 17+  | 后端运行时 |
| **PostgreSQL** | 14+  | 数据库     |
| **Node.js**    | 18+  | 前端构建   |
| **npm**        | 8+   | 包管理器   |

### 方式一：Docker 快速启动（推荐）

```bash
cd D:\hospital\hospital
deploy.bat              # Windows
# 或
./deploy.sh            # Linux/Mac
```

启动完成后访问：

- **前端**：http://localhost
- **后端 API**：http://localhost:9090/api
- **健康检查**：http://localhost:9090/api/health

### 方式二：本地开发环境

```bash
# 启动后端
mvn spring-boot:run
# 访问 http://localhost:9090

# 启动前端（新窗口）
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

### 方式三：使用脚本快速部署（Linux/Mac/Windows）

如果您已经安装了 Docker Desktop，可以直接运行下面的命令：

```bash
# 构建并启动所有服务（后台运行）
docker-compose up -d --build

# 查看运行状态
docker-compose ps

# 查看后端日志
docker-compose logs -f backend
```

> **注意**：首次启动可能需要几分钟下载基础镜像和构建依赖。

### 数据持久化与清理

项目使用 Docker Volume 持久化关键数据：

- `postgres_data`：PostgreSQL 数据文件
- `prometheus_data`：Prometheus 监控数据
- `grafana_data`：Grafana 仪表盘配置

如果需要彻底重置环境（**会删除数据库数据**）：

```bash
docker-compose down -v
```

### 生产环境部署建议

1. 修改 `.env` 中所有默认密码与密钥，尤其是 `DB_PASSWORD`、`JWT_SECRET`、`INIT_ADMIN_PASSWORD`。
2. 生产环境建议将 `INIT_ADMIN_ENABLED=false`，避免每次部署时重复初始化管理员。
3. 如需启用智能体工作流，请正确配置 `COZE_API_URL`、`COZE_API_KEY`、`COZE_WORKFLOW_ID`。
4. 建议将前端部署在 Nginx / CDN 后，域名通过 HTTPS 访问，后端仅开放 API 端口。
5. 建议定期备份 PostgreSQL 数据卷，并将日志目录 `./logs` 映射到宿主机持久化。

---

## 📚 功能说明

### 账号与权限

- 邮箱注册、JWT 登录
- 5 级权限控制
- 管理员密码重置

### 排班管理

- 班次创建、指派、删除
- 状态管理（OPEN → ASSIGNED → COMPLETED）
- 冲突检测、数据统计

### 实时通信

- WebSocket 多频道：`/topic/shifts`、`/topic/agent-chat` 等
- 聊天历史持久化
- 实时任务推送

### 智能体协作

- 任务创建与跟踪
- 实时聊天
- 多端同步

---

## 🛠 技术栈

- **后端**：Spring Boot 3.2.1 + PostgreSQL 14+
- **前端**：Vue 3 + Vite + Fetch + WebSocket
- **容器**：Docker + Docker Compose
- **监控**：Prometheus + Grafana（可选）

---

## ⚙️ 配置

### 环境变量 (.env)

下面是项目部署时最核心的配置项：

| 变量名                   | 默认值                                | 说明                           |
| ------------------------ | ------------------------------------- | ------------------------------ |
| `DB_HOST`                | `postgres`                            | 主业务数据库地址               |
| `DB_PORT`                | `5432`                                | 主业务数据库端口               |
| `DB_NAME`                | `hospital_analytics`                  | 主数据库名称                   |
| `DB_USER`                | `postgres`                            | 主数据库用户名                 |
| `DB_PASSWORD`            | `postgres`                            | 主数据库密码，生产必须修改     |
| `ANALYTICS_ENABLED`      | `true`                                | 是否启用可视化/分析数据能力    |
| `ANALYTICS_DB_HOST`      | `postgres`                            | 分析数据库地址                 |
| `ANALYTICS_DB_PORT`      | `5432`                                | 分析数据库端口                 |
| `ANALYTICS_DB_NAME`      | `hospital_analytics`                  | 分析数据库名称，默认与主库一致 |
| `ANALYTICS_DB_USER`      | `postgres`                            | 分析数据库用户名               |
| `ANALYTICS_DB_PASSWORD`  | `postgres`                            | 分析数据库密码                 |
| `SPRING_PROFILES_ACTIVE` | `prod`                                | 部署环境 profile               |
| `JWT_SECRET`             | -                                     | JWT 密钥，生产必须修改         |
| `JWT_EXPIRATION_MINUTES` | `60`                                  | Token 过期时间（分钟）         |
| `CORS_ALLOWED_ORIGINS`   | 本地开发地址集合                      | 允许跨域访问的前端来源         |
| `WS_ALLOWED_ORIGINS`     | 本地开发地址集合                      | WebSocket 允许来源             |
| `JAVA_OPTS`              | `-Xms256m -Xmx512m ...`               | JVM 内存与编码参数             |
| `INIT_ADMIN_ENABLED`     | `false`                               | 是否在启动时初始化默认管理员   |
| `INIT_ADMIN_EMAIL`       | `admin@hospital.local`                | 默认管理员邮箱                 |
| `INIT_ADMIN_PASSWORD`    | `Admin123!`                           | 默认管理员密码                 |
| `INIT_ADMIN_FULL_NAME`   | `System Admin`                        | 默认管理员姓名                 |
| `COZE_API_URL`           | `http://localhost:8000/api/coze/chat` | Coze 代理服务地址              |
| `COZE_API_KEY`           | 空                                    | Coze API 密钥                  |
| `COZE_WORKFLOW_ID`       | 空                                    | Coze 工作流 ID                 |
| `BACKEND_PORT`           | `9090`                                | 后端宿主机映射端口             |
| `FRONTEND_PORT`          | `80`                                  | 前端宿主机映射端口             |
| `PROMETHEUS_PORT`        | `9091`                                | Prometheus 映射端口            |
| `GRAFANA_PORT`           | `3000`                                | Grafana 映射端口               |

示例配置：

```bash
# 数据库
DB_HOST=postgres
DB_PORT=5432
DB_NAME=hospital_analytics
DB_USER=postgres
DB_PASSWORD=your-strong-password

# 分析库（单机部署可与主库一致）
ANALYTICS_ENABLED=true
ANALYTICS_DB_HOST=postgres
ANALYTICS_DB_PORT=5432
ANALYTICS_DB_NAME=hospital_analytics
ANALYTICS_DB_USER=postgres
ANALYTICS_DB_PASSWORD=your-strong-password

# Spring Boot
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=replace-with-a-long-random-secret
JWT_EXPIRATION_MINUTES=120
JAVA_OPTS=-Xms512m -Xmx1024m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai

# 初始化管理员
INIT_ADMIN_ENABLED=false
INIT_ADMIN_EMAIL=admin@your-domain.com
INIT_ADMIN_PASSWORD=replace-admin-password
INIT_ADMIN_FULL_NAME=System Admin

# Coze
COZE_API_URL=http://your-coze-proxy:8000/api/coze/chat
COZE_API_KEY=pat_xxx
COZE_WORKFLOW_ID=7xxxxxxxxxxxx
```

### 默认账号

| 用户   | 邮箱                 | 密码      |
| ------ | -------------------- | --------- |
| 管理员 | admin@hospital.local | Admin123! |
| 数据库 | postgres             | postgres  |

⚠️ **生产环境务必修改所有密码与密钥，并关闭默认管理员自动初始化。**

---

## 🐳 Docker 部署

```bash
# 构建并后台启动
docker-compose up -d --build

# 仅启动核心服务（数据库、后端、前端）
docker-compose up -d postgres backend frontend

# 启动监控栈
docker-compose --profile monitoring up -d prometheus grafana

# 查看服务状态
docker-compose ps

# 查看后端日志
docker-compose logs -f backend

# 停止服务
docker-compose down
```

默认访问地址：

- 前端：`http://localhost`
- 后端：`http://localhost:9090`
- 健康检查：`http://localhost:9090/api/health`
- Actuator：`http://localhost:9090/actuator`
- Prometheus（可选）：`http://localhost:9091`
- Grafana（可选）：`http://localhost:3000`

### 部署代码已做的调整

- Docker Compose 默认数据库名已与 Spring Boot 配置一致为 `hospital_analytics`
- 后端容器默认以 `prod` profile 启动
- 已补充分析数据源环境变量，避免可视化模块部署时缺少配置
- 后端镜像已支持 `JAVA_OPTS` 注入，便于线上调整 JVM 参数
- 前端 Nginx 已开启 Gzip 压缩，提升静态资源传输效率

---

## 📡 API 概览

```
认证:    POST /api/auth/login, /api/auth/register
排班:    GET|POST|PUT|DELETE /api/shifts
科室:    GET|POST /api/departments
智能体:  GET|POST /api/agent/tasks, GET /api/agent/chat
管理:    GET /api/admin/users, PUT /api/admin/users/{userId}/password
```

完整文档：启动后访问 `http://localhost:9090/swagger-ui.html`

---

## 🔐 安全建议

- JWT 无状态认证
- Spring Security 权限控制
- BCrypt 密码加密
- CORS 配置
- SQL 注入防护

生产环境步骤：

```bash
# 1. 修改所有密码（.env）
# 2. 生成 JWT 密钥：openssl rand -base64 32
# 3. 启用 HTTPS（在 Nginx 配置 SSL）
# 4. 定期备份数据库
```

---

## 🔧 故障排查

| 问题               | 解决方案                                                     |
| ------------------ | ------------------------------------------------------------ |
| 端口被占用         | 修改 `.env` 中的端口号                                       |
| 数据库连接失败     | 检查 PostgreSQL 运行状态、库名是否为 `hospital_analytics`、连接参数是否一致 |
| 前端无法访问 API   | 检查 CORS 配置，确保后端在 9090 运行                         |
| Docker 启动失败    | 运行 `docker-compose logs` 查看错误                          |
| Java 版本不兼容    | 确认 Java 17+（`java -version`）                             |
| 可视化无数据       | 检查 `ANALYTICS_ENABLED` 与分析数据源配置，确认初始化脚本已执行 |
| 智能体仍为演示模式 | 检查 `COZE_API_URL`、`COZE_API_KEY`、`COZE_WORKFLOW_ID` 是否已正确配置 |

查看日志：

```bash
docker-compose logs -f backend
tail -f logs/hospital.log
```

---

## 📈 项目改进

- 全局异常处理、结构化日志、数据库优化、监控告警等
- 一键启动、生产配置、云部署

详见：

- `IMPROVEMENT_SUMMARY.md` - 改进总结
- `DOCKER_COMPLETE_GUIDE.md` - Docker 指南

---

## 📚 文档导航

| 文档                        | 说明            |
| --------------------------- | --------------- |
| `QUICK_START_GUIDE.md`      | 快速开始        |
| `DOCKER_QUICK_REFERENCE.md` | Docker 命令速查 |
| `DOCKER_DEPLOYMENT.md`      | Docker 完整指南 |
| `DOCKER_COMPLETE_GUIDE.md`  | Docker 终极指南 |
| `IMPROVEMENT_SUMMARY.md`    | 代码改进总结    |

---

## 💡 项目结构

```
hospital/
├── src/main/java/org/example/hospital/
│   ├── config/          配置
│   ├── controller/      REST API
│   ├── domain/          数据模型
│   ├── dto/             数据传输对象
│   ├── repository/      数据访问
│   ├── security/        安全配置
│   └── service/         业务逻辑
├── frontend/            Vue 应用
├── Dockerfile           后端镜像
├── docker-compose.yml   容器编排
├── .env                 环境配置
└── README.md            本文件
```

---

## 📄 许可证

MIT License

---

<div align="center">


**⭐ 如果这个项目对你有帮助，请给个 Star！**

Made with ❤️ for Hospital Management

</div>
