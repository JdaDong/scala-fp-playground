package demo.part13

import cats.effect.{IO, Ref}
import cats.mtl.{Ask, Raise, Tell}
import cats.syntax.all.*

import io.circe.Encoder
import io.circe.generic.semiauto.*
import io.circe.syntax.*

import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*

import Domain.*

/**
 * ============================================================
 * TodoRoutes —— HTTP 路由层（http4s）
 *
 *   ★ 这里采用"生产型" interpreter：F = IO
 *     - 为 IO 手写 Ask / Raise / Tell 三个 given 实例
 *     - Ask  : 直接 IO.pure(cfg)
 *     - Raise: 用一个自定义异常包装（IO.raiseError），再在 runF 里 attempt 还原
 *     - Tell : 用 Ref[List[String]] 累积，每个请求独立一份
 *
 *   对比 part12/Scene03 的 Interpreter B，一模一样的思路。
 *   好处：零 transformer 栈，性能最好，日志和堆栈清晰。
 * ============================================================
 */
object TodoRoutes {

  // ============================================================
  // IO 级别的 given 实例
  // ============================================================

  // 把业务错误包成 Throwable（只用作 IO 的短路通道）
  private final case class TodoErrorWrap(err: TodoError) extends RuntimeException(err.toString)

  private given raiseIO: Raise[IO, TodoError] with {
    def functor = cats.Functor[IO]
    def raise[E2 <: TodoError, A](e: E2): IO[A] = IO.raiseError(TodoErrorWrap(e))
  }

  private def askFromConfig(cfg: AppConfig): Ask[IO, AppConfig] = new Ask[IO, AppConfig] {
    def applicative = cats.Applicative[IO]
    def ask[E2 >: AppConfig]: IO[E2] = IO.pure(cfg)
  }

  private def tellFromRef(ref: Ref[IO, List[String]]): Tell[IO, List[String]] = new Tell[IO, List[String]] {
    def functor = cats.Functor[IO]
    def tell(l: List[String]): IO[Unit] = ref.update(_ ++ l)
  }

  // ============================================================
  // 错误 → HTTP 响应
  // ============================================================
  final case class ApiError(error: String, details: String)
  object ApiError {
    given Encoder[ApiError] = deriveEncoder
  }

  private def errorToResponse(err: TodoError): IO[Response[IO]] = err match {
    case TodoError.NotFound(id) =>
      NotFound(ApiError("not_found", s"Todo not found: ${id.value}").asJson)
    case TodoError.InvalidInput(reason) =>
      BadRequest(ApiError("invalid_input", reason).asJson)
    case TodoError.DbError(cause) =>
      InternalServerError(ApiError("db_error", cause).asJson)
  }

  // 跑一次业务调用：创建请求级 audit Ref + 调用 + 打日志 + 错误翻译
  private def runCall[A](
      cfg: AppConfig, repo: TodoRepo
  )(use: TodoService[IO] => IO[A])(onSuccess: A => IO[Response[IO]]): IO[Response[IO]] = {
    for {
      auditRef <- Ref.of[IO, List[String]](Nil)
      // 安装 given
      given Ask[IO, AppConfig]     = askFromConfig(cfg)
      given Tell[IO, List[String]] = tellFromRef(auditRef)
      // raiseIO 已在 object 范围内
      liftIO: ([A] => IO[A] => IO[A]) = [A] => (io: IO[A]) => io
      service = TodoService[IO](repo, liftIO)

      attempted <- use(service).attempt
      logs      <- auditRef.get
      _         <- logs.traverse_(m => IO.println(s"  [audit] $m"))

      resp      <- attempted match {
                     case Right(a)                    => onSuccess(a)
                     case Left(TodoErrorWrap(err))    => errorToResponse(err)
                     case Left(other)                 => IO.raiseError(other)  // 非业务错抛出
                   }
    } yield resp
  }

  // ============================================================
  // 路由
  // ============================================================
  def routes(cfg: AppConfig, repo: TodoRepo): HttpRoutes[IO] = {

    HttpRoutes.of[IO] {

      // GET /todos
      case GET -> Root / "todos" =>
        runCall(cfg, repo)(_.list)(ts => Ok(ts.asJson))

      // POST /todos
      case req @ POST -> Root / "todos" =>
        req.as[CreateTodo].flatMap { body =>
          runCall(cfg, repo)(_.create(body))(t => Created(t.asJson))
        }

      // GET /todos/{id}
      case GET -> Root / "todos" / id =>
        TodoId.fromString(id) match {
          case Left(msg)  => BadRequest(ApiError("invalid_id", msg).asJson)
          case Right(tid) => runCall(cfg, repo)(_.get(tid))(t => Ok(t.asJson))
        }

      // PUT /todos/{id}
      case req @ PUT -> Root / "todos" / id =>
        TodoId.fromString(id) match {
          case Left(msg)  => BadRequest(ApiError("invalid_id", msg).asJson)
          case Right(tid) =>
            req.as[UpdateTodo].flatMap { body =>
              runCall(cfg, repo)(_.update(tid, body))(t => Ok(t.asJson))
            }
        }

      // DELETE /todos/{id}
      case DELETE -> Root / "todos" / id =>
        TodoId.fromString(id) match {
          case Left(msg)  => BadRequest(ApiError("invalid_id", msg).asJson)
          case Right(tid) => runCall(cfg, repo)(_.delete(tid))(_ => NoContent())
        }

      // 健康检查
      case GET -> Root / "health" => Ok("""{"status":"ok"}""")
    }
  }
}
