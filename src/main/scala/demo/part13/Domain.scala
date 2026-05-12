package demo.part13

import java.time.Instant
import java.util.UUID

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

/**
 * ============================================================
 * Domain —— 领域模型 + 业务错误
 *
 *   采用"错误是值，不抛"的 FP 风格：
 *     - TodoError 是一个 sealed trait 层次
 *     - 服务层通过 cats-mtl 的 Raise[F, TodoError] 短路
 *     - 路由层在最后一层把它翻译成对应的 HTTP 状态码
 * ============================================================
 */
object Domain {

  // ============================================================
  // 核心实体
  // ============================================================
  opaque type TodoId = UUID
  object TodoId {
    def apply(u: UUID): TodoId         = u
    def random: TodoId                 = UUID.randomUUID()
    def fromString(s: String): Either[String, TodoId] =
      try Right(UUID.fromString(s)) catch { case _: IllegalArgumentException => Left(s"非法 UUID: $s") }
    extension (id: TodoId) def value: UUID = id

    // 在 opaque type 所在作用域内定义 Encoder/Decoder —— 这里能看到 TodoId = UUID
    given Encoder[TodoId] = Encoder.encodeUUID.contramap[TodoId](identity)
    given Decoder[TodoId] = Decoder.decodeUUID.map(TodoId.apply)
  }

  // ============================================================
  // Todo 实体
  // ============================================================
  case class Todo(
    id:        TodoId,
    title:     String,
    completed: Boolean,
    createdAt: Instant,
    updatedAt: Instant
  )
  object Todo {
    given Encoder[Todo] = deriveEncoder
    given Decoder[Todo] = deriveDecoder
  }

  // ============================================================
  // DTO：创建/更新请求体
  // ============================================================
  case class CreateTodo(title: String)
  object CreateTodo {
    given Encoder[CreateTodo] = deriveEncoder
    given Decoder[CreateTodo] = deriveDecoder
  }

  case class UpdateTodo(title: Option[String], completed: Option[Boolean])
  object UpdateTodo {
    given Encoder[UpdateTodo] = deriveEncoder
    given Decoder[UpdateTodo] = deriveDecoder
  }

  // ============================================================
  // 业务错误
  // ============================================================
  sealed trait TodoError extends Product with Serializable
  object TodoError {
    case class  NotFound(id: TodoId)          extends TodoError
    case class  InvalidInput(reason: String)  extends TodoError
    case class  DbError(cause: String)        extends TodoError
  }
}
