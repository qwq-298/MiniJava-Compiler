# MiniJava Online Compiler

一个在线的 MiniJava 语言编译与可视化 IDE，支持在浏览器中编写、运行 MiniJava 代码，并提供 AST 语法树可视化。

---

## 🚀 快速启动

### 环境要求

| 工具 | 版本 |
|------|------|
| Java JDK | 21+ |
| Maven | 3.9+ |
| Node.js | 20.19+ 或 22.12+ |
| MySQL | 8.0+ |

### 1. 配置数据库

先创建 MySQL 数据库：

```sql
CREATE DATABASE minijava;
```

然后编辑 `vis/src/main/resources/application.properties`，修改数据库连接信息：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/minijava
spring.datasource.username=你的用户名
spring.datasource.password=你的密码
```

### 2. 一键启动

```powershell
powershell -ExecutionPolicy Bypass -File start-dev.ps1
```

该脚本会自动打开两个窗口：
- **后端**：Spring Boot (vis)，运行在 `http://localhost:8080`
- **前端**：Vite Dev Server (Vue/minijava)，运行在 `http://localhost:5173`

---

## 🏗️ 项目架构

```
MiniJavaCompiler/
├── vis/                        # 后端 Spring Boot
│   ├── src/main/java/com/mjiv/vis/
│   │   ├── interpreter/        # 词法/语法解析 & 解释器 (ANTLR4)
│   │   ├── ast/                # AST 构建与 JSON 序列化
│   │   ├── controller/         # REST API 控制器
│   │   ├── service/            # 业务逻辑层
│   │   ├── entity/             # JPA 实体 (User, FileInfo)
│   │   ├── dto/                # 数据传输对象
│   │   ├── repository/         # JPA 数据访问层
│   │   └── run/                # 运行结果封装
│   └── storage/                # 用户文件物理存储
├── Vue/minijava/               # 前端 Vue 3
│   └── src/
│       ├── pages/              # 页面 (LoginPage, RegisterPage, IDEPage)
│       ├── components/         # 组件 (ASTViewer, AstNode, MindMapNode)
│       ├── api/                # API 封装 (auth, code, file)
│       └── router/             # 路由配置
└── start-dev.ps1               # 一键启动脚本
```

---

## 📦 技术栈

### 后端

| 技术 | 用途 |
|------|------|
| Spring Boot 4.0 | Web MVC 框架 |
| ANTLR 4.13 | 词法/语法解析器生成 |
| Spring Data JPA | ORM 数据访问 |
| MySQL | 数据库 |
| Lombok | 简化 Java 代码 |

### 前端

| 技术 | 用途 |
|------|------|
| Vue 3 (Composition API) | UI 框架 |
| Vite 8 | 开发服务器 & 构建工具 |
| TypeScript 6 | 类型安全 |
| Monaco Editor | 代码编辑器 |
| Vue Router 5 | 前端路由 |
| Axios | HTTP 客户端 |

---

## ✨ 功能特性

- **代码编辑** — 基于 Monaco Editor，支持语法高亮、字体调节
- **在线编译运行** — 发送代码到后端解释执行，实时返回输出
- **AST 可视化** — 解析代码生成抽象语法树，支持**列表视图**与**思维导图**两种展示模式
- **文件管理** — 创建、保存、打开、删除 MiniJava 源文件，支持文件上传
- **批量运行** — 同时运行所有已打开文件并汇总输出
- **用户系统** — 注册 / 登录，每个用户拥有独立的文件空间

---

## 🔌 API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/run` | 运行 MiniJava 代码 |
| POST | `/api/ast` | 生成 AST JSON |
| POST | `/api/file/save` | 保存文件 |
| GET | `/api/file/list` | 获取文件列表 |
| GET | `/api/file/open` | 打开文件 |
| DELETE | `/api/file/delete` | 删除文件 |
| POST | `/api/file/upload` | 上传文件 |
| POST | `/api/file/run-all` | 批量运行文件 |
| POST | `/api/login/register` | 用户注册 |
| POST | `/api/login/login` | 用户登录 |
| GET | `/api/user/by-username` | 查询用户 ID |

---

## 🧪 运行测试

```bash
cd vis
mvn test
```

---

## 📝 MiniJava 语言示例

```java
class Main {
    int main() {
        println("Hello, MiniJava!");
        int x = 42;
        println(x);
        return 0;
    }
}
```

支持的特性：
- 基本类型：`int`, `boolean`, `char`, `void`
- 控制流：`if/else`, `while`, `for`
- 类 & 继承 (`extends`)，构造函数，`super` 调用
- 一维/多维数组，数组字面量
- 类型转换 (`(int)`, `(char)`)
- 内置函数：`print`, `println`, `length`, `atoi`, `to_string`
- `var` 类型推断，`instanceof` 检查

---

## 📄 License

MIT