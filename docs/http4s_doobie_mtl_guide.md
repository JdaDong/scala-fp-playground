# http4s + doobie + cats-mtl —— 最经典三件套 REST 服务

> 配套代码：`src/main/scala/demo/part13/`
>
> 目标：搭建一个**完整可运行的 Todo REST 服务**，把前面 12 个 part 学到的东西落在一个真实项目里。

---

## 📐 架构一览

```
┌────────────────────────────────────────────────────────────────┐
│                        HTTP 请求                                │
└──────────────────────────────┬─────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   TodoRoutes.scala  │   ← http4s 路由层
                    │   （IO + 请求级 Ref）  │
                    │   - URL 匹配         │
                    │   - Raise/Tell/Ask  │   ← cats-mtl given 实例
                    │   - 错误→HTTP 状态码 │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │ TodoService.scala   │   ← 业务层
                    │ [F[_]: Monad: Clock]│
                    │ + Ask / Raise / Tell│   ← 对 F 多态
                    │ - 校验、组合、审计    │
                    └──────────┬──────────┘
                               │ liftIO
                    ┌──────────▼──────────┐
                    │   TodoRepo.scala    │   ← 数据层（doobie）
                    │   - sql"..." 查询   │
                    │   - .transact(xa)   │
                    └──────────┬──────────┘
                               │
                       ┌───────▼────────┐
                       │   H2 数据库     │   ← HikariCP 连接池
                       │  (内存，零配置) │
                       └────────────────┘
```

---

## 📦 组件依赖（build.sbt）

```scala
libraryDependencies ++= Seq(
  // http4s
  "org.http4s"    %% "http4s-ember-server" % "0.23.27",
  "org.http4s"    %% "http4s-ember-client" % "0.23.27",
  "org.http4s"    %% "http4s-dsl"          % "0.23.27",
  "org.http4s"    %% "http4s-circe"        % "0.23.27",

  // JSON
  "io.circe"      %% "circe-generic"       % "0.14.9",

  // doobie + H2
  "org.tpolecat"  %% "doobie-core"         % "1.0.0-RC5",
  "org.tpolecat"  %% "doobie-hikari"       % "1.0.0-RC5",
  "org.tpolecat"  %% "doobie-h2"           % "1.0.0-RC5",

  // 日志
  "ch.qos.logback" %  "logback-classic"    % "1.5.6"
)
```

---

## 🗂️ 6 个源文件解构

### 1. `Domain.scala` —— 领域模型 + 错误

```scala
opaque type TodoId = UUID
object TodoId {
  def random: TodoId = UUID.randomUUID()
  extension (id: TodoId) def value: UUID = id
  given Encoder[TodoId] = Encoder.encodeUUID.contramap(identity)  // ← 装在 companion 里
  given Decoder[TodoId] = Decoder.decodeUUID.map(TodoId.apply)
}

case class Todo(id: TodoId, title: String, completed: Boolean,
                createdAt: Instant, updatedAt: Instant)

// 业务错误（不是 Throwable，是 ADT）
sealed trait TodoError
case class  NotFound(id: TodoId)         extends TodoError
case class  InvalidInput(reason: String) extends TodoError
case class  DbError(cause: String)       extends TodoError
```

**要点**：
- 用 **opaque type** 做类型安全 ID（part11 技术）
- 用 **ADT** 表达错误（不抛）
- **circe** 自动派生 JSON 编解码

### 2. `Config.scala` —— 分层配置

```scala
final case class DbConfig(url: String, user: String, password: String, poolSize: Int)
final case class ServerConfig(host: String, port: Int)
final case class BizConfig(maxTitleLength: Int)
final case class AppConfig(db: DbConfig, server: ServerConfig, biz: BizConfig)
```

业务用到的是 `BizConfig.maxTitleLength` —— 通过 `cats-mtl.Ask` 注入服务层。

### 3. `TodoRepo.scala` —— doobie 数据层

```scala
// 设计关键：用 Row case class 避免大 Tuple 推断问题
private case class TodoRow(id: String, title: String, completed: Boolean,
                           createdAt: Instant, updatedAt: Instant)

def insert(t: Todo): IO[Unit] = {
  val r = toRow(t)
  sql"""
    INSERT INTO todos (id, title, completed, created_at, updated_at)
    VALUES (${r.id}, ${r.title}, ${r.completed}, ${r.createdAt}, ${r.updatedAt})
  """.update.run.transact(xa).void
}

def findById(id: TodoId): IO[Option[Todo]] =
  sql"SELECT id, title, completed, created_at, updated_at FROM todos WHERE id = ${id.value.toString}"
    .query[TodoRow].option.map(_.map(_.toDomain)).transact(xa)
```

**要点**：
- `sql"..."` 自动参数化（防 SQL 注入）
- `.transact(xa)` 在 HikariCP 连接池里执行事务
- UUID 用 `VARCHAR(36)` 存（跨方言最稳）

### 4. `TodoService.scala` —— 业务层（cats-mtl 武装）

```scala
class TodoService[F[_]: Monad: Clock](
    repo:   TodoRepo,
    liftIO: [A] => IO[A] => F[A]         // ← polymorphic function lift
)(using
    cfg:   Ask[F, AppConfig],            // ← 读配置
    raise: Raise[F, TodoError],          // ← 业务短路
    tell:  Tell[F, List[String]]         // ← 审计日志
) {

  def create(req: CreateTodo): F[Todo] = for {
    c    <- cfg.ask                              // 零 lift
    _    <- if (req.title.trim.isEmpty)
              raise.raise(InvalidInput("title 不能为空")): F[Unit]
            else if (req.title.length > c.biz.maxTitleLength)
              raise.raise(InvalidInput(s"超过 ${c.biz.maxTitleLength}")): F[Unit]
            else Monad[F].unit
    t    <- now
    todo =  Todo(TodoId.random, req.title.trim, false, t, t)
    _    <- liftIO(repo.insert(todo))
    _    <- tell.tell(List(s"CREATE ${todo.id.value} \"${todo.title}\""))
  } yield todo
}
```

**要点**：
- 对 `F` **完全多态**
- 通过 `liftIO` 把 `IO[A]` 提升到 `F[A]`（生产选 `F=IO` 时就是 `identity`）
- 校验 → `raise.raise(...)` 直接短路

### 5. `TodoRoutes.scala` —— http4s 路由 + IO-level cats-mtl

这里选择的 interpreter 是 **part12/Scene03 的 Interpreter B 风格**：**`F = IO` 直接手写 3 个 given**。

```scala
// 业务错误→异常包装器（只在 IO 内部走这个通道）
private case class TodoErrorWrap(err: TodoError) extends RuntimeException

private given raiseIO: Raise[IO, TodoError] with {
  def functor = Functor[IO]
  def raise[E2 <: TodoError, A](e: E2): IO[A] = IO.raiseError(TodoErrorWrap(e))
}

private def askFromConfig(cfg: AppConfig): Ask[IO, AppConfig] = new Ask[IO, AppConfig] {
  def applicative = Applicative[IO]
  def ask[E2 >: AppConfig]: IO[E2] = IO.pure(cfg)
}

private def tellFromRef(ref: Ref[IO, List[String]]): Tell[IO, List[String]] =
  new Tell[IO, List[String]] {
    def functor = Functor[IO]
    def tell(l: List[String]): IO[Unit] = ref.update(_ ++ l)
  }
```

每个请求**各自建一个 `Ref[List[String]]`**（请求级审计缓冲），响应结束时批量打出：

```scala
private def runCall[A](...)(use: TodoService[IO] => IO[A])(onSuccess: A => IO[Response[IO]]): IO[Response[IO]] =
  for {
    auditRef <- Ref.of[IO, List[String]](Nil)
    given Ask[IO, AppConfig]     = askFromConfig(cfg)
    given Tell[IO, List[String]] = tellFromRef(auditRef)
    service   = TodoService[IO](repo, [A] => (io: IO[A]) => io)

    attempted <- use(service).attempt
    logs      <- auditRef.get
    _         <- logs.traverse_(m => IO.println(s"  [audit] $m"))

    resp      <- attempted match {
                   case Right(a)                  => onSuccess(a)
                   case Left(TodoErrorWrap(err))  => errorToResponse(err)  // → 400 / 404 / 500
                   case Left(other)               => IO.raiseError(other)
                 }
  } yield resp
```

路由定义一目了然：

```scala
HttpRoutes.of[IO] {
  case GET  -> Root / "todos"       => runCall(cfg, repo)(_.list)(ts => Ok(ts.asJson))
  case req @ POST -> Root / "todos" => req.as[CreateTodo].flatMap(b => runCall(..)(_.create(b))(t => Created(t.asJson)))
  case GET  -> Root / "todos" / id  => TodoId.fromString(id) match { ... }
  case req @ PUT  -> Root / "todos" / id => ...
  case DELETE -> Root / "todos" / id => ...
  case GET  -> Root / "health"       => Ok("""{"status":"ok"}""")
}
```

### 6. `Main.scala` —— 入口装配

```scala
object Main extends IOApp {

  private def transactor(cfg: DbConfig): Resource[IO, HikariTransactor[IO]] =
    for {
      ec <- ExecutionContexts.fixedThreadPool[IO](cfg.poolSize)
      xa <- HikariTransactor.newHikariTransactor[IO](
              "org.h2.Driver", cfg.url, cfg.user, cfg.password, ec)
    } yield xa

  override def run(args: List[String]): IO[ExitCode] = {
    val cfg = AppConfig.default

    val app: Resource[IO, Server] = for {
      xa   <- transactor(cfg.db)
      repo =  TodoRepo(xa)
      _    <- Resource.eval(repo.initSchema)
      srv  <- EmberServerBuilder.default[IO]
                .withHost(Host.fromString(cfg.server.host).get)
                .withPort(Port.fromInt(cfg.server.port).get)
                .withHttpApp(TodoRoutes.routes(cfg, repo).orNotFound)
                .build
    } yield srv

    app.useForever.as(ExitCode.Success)
  }
}
```

**要点**：整个应用是一个 `Resource`：连接池、数据库、HTTP Server 全部用 cats-effect `Resource` 管理，保证优雅关闭。

---

## 🧪 端到端测试实录

启动：
```bash
sbt "runMain demo.part13.Main"
```

### 14 组请求测试结果

| # | 请求 | 实际响应 | 预期 |
|---|---|---|---|
| 1 | `GET /health` | `"{\"status\":\"ok\"}"` | ✅ |
| 2 | `GET /todos` | `[]` | ✅ 空列表 |
| 3 | `POST /todos {"title":"Learn http4s"}` | `{id:..., title, completed:false, ...}` | ✅ 201 Created |
| 4 | `POST /todos {"title":"Learn doobie"}` | `{id:..., ...}` | ✅ 201 |
| 5 | `POST /todos {"title":"   "}` | `{"error":"invalid_input","details":"title 不能为空"}` | ✅ **400** |
| 6 | `GET /todos` | 2 条记录 | ✅ |
| 7 | `GET /todos/{id1}` | 完整 Todo | ✅ |
| 8 | `PUT /todos/{id1} {"completed":true}` | 更新后 Todo（`completed=true`，`updatedAt` 变） | ✅ |
| 9 | `PUT /todos/{id2} {"title":"..."}` | 更新后 Todo | ✅ |
| 10 | `GET /todos/000000...` | `{"error":"not_found",...}` | ✅ **404** |
| 11 | `GET /todos/not-a-uuid` | `{"error":"invalid_id",...}` | ✅ **400** |
| 12 | `DELETE /todos/{id1}` | 空 | ✅ **204** |
| 13 | `DELETE /todos/{id1}` 再次 | `{"error":"not_found",...}` | ✅ **404** |
| 14 | `GET /todos` | 只剩 1 条 | ✅ |

### 服务端审计日志（`cats-mtl.Tell` 收集）

```
[audit] CREATE 16720280-e665-4f5b-aa8f-5b54960be7ca "Learn http4s"
[audit] CREATE c9ed4653-6dde-45da-a5d7-802241e25c6b "Learn doobie"
[audit] UPDATE 16720280-e665-4f5b-aa8f-5b54960be7ca "Learn http4s" completed=true
[audit] UPDATE c9ed4653-6dde-45da-a5d7-802241e25c6b "Learn doobie deeply" completed=false
[audit] DELETE 16720280-e665-4f5b-aa8f-5b54960be7ca
```

5 次写操作都被 `Tell` 捕获 → 请求末尾一次性 flush。

---

## 🎓 这个项目印证了前面 12 个 part 的所有知识点

| 技术 | 对应 part | 在本项目中的体现 |
|---|---|---|
| opaque type + phantom tag | part11 | `TodoId` |
| case class + ADT | part05 | `Todo`, `TodoError` |
| for-comprehension | part05 | 业务组合全用 for |
| Cats Effect IO / Resource | part06 | `Transactor` / `Server` 都是 Resource |
| Type Class + 派生 | part07 | circe Encoder/Decoder |
| Tagless Final | part08 | `TodoService[F[_]: Monad]` |
| fs2 | part10 | 本例未直接用，但 http4s body 底层就是 fs2 |
| HKT + Variance | part11 | `[F[_]]` + `Ask[F, +R]` 等 |
| cats-mtl | part12 | `Ask / Raise / Tell` 武装业务层 |

---

## 🚀 运行方式

```bash
# 启动服务
sbt "runMain demo.part13.Main"

# 另一个终端：测试
curl http://localhost:8080/health

curl -X POST http://localhost:8080/todos \
  -H 'Content-Type: application/json' \
  -d '{"title":"Learn http4s"}'

curl http://localhost:8080/todos

curl -X PUT http://localhost:8080/todos/<id> \
  -H 'Content-Type: application/json' \
  -d '{"completed":true}'

curl -X DELETE http://localhost:8080/todos/<id>
```

---

## 🧠 七句核心心法

1. **分层**：Routes（http4s） → Service（cats-mtl）→ Repo（doobie）→ DB，每层职责单一
2. **Repo 返回 `IO`**：因为 JDBC 是硬依赖，不必再抽象
3. **Service 对 `F` 多态**：要么在生产用 `F=IO` + 手写 given，要么测试用 `F=EitherT[Writer[Id]...]`
4. **Routes 挑具体 F**：把 `Raise/Tell/Ask` 的 given 装进请求作用域
5. **错误是值**：`TodoError` 是 ADT，只在边界层包装成 `Throwable` 走 `IO.raiseError`
6. **Resource 管一切**：连接池、HTTP Server 都是 `Resource`，Ctrl-C 也能优雅关闭
7. **审计日志**：`Ref[List[String]]` 作为 `Tell` 的 buffer，请求结束批量 flush —— 完美结构化

---

## 🛠️ 进阶方向

| 主题 | 怎么做 |
|---|---|
| 替换为 PostgreSQL | 改 `DbConfig.url` + 依赖换 `doobie-postgres` + 列类型换原生 UUID |
| 加认证 | 用 http4s 的 `AuthMiddleware` + JWT |
| 自动生成 OpenAPI | 换 `tapir` 替代 http4s-dsl，自动出 swagger |
| 分布式追踪 | 用 `natchez` 接入 Jaeger（它就是靠 cats-mtl 风格做的）|
| 流式响应 | 大列表用 fs2 `Stream` 分块输出（zero-copy）|
| 高并发优化 | `TodoService` 参数化 `F[_]`，替换 interpreter 不改业务 |

---

## 🎉 至此

你已经拥有一个**生产可借鉴的 Scala 函数式 Web 服务骨架**。

把前面 12 个 part 的语言特性、函数式模式、类型系统、能力组合全部**打包成了一份 ~350 行的、可运行的代码**。

> 真实项目只是在这个骨架上加：更多路由、更多表、认证、日志、监控。
> 架构不需要变。
