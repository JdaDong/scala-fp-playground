package demo.part13

import java.time.Instant

import cats.Monad
import cats.effect.IO
import cats.effect.kernel.Clock
import cats.mtl.{Ask, Raise, Tell}
import cats.syntax.all.*

import Domain.*

/**
 * ============================================================
 * TodoService —— 业务层（cats-mtl 武装）
 *
 *   本层"只做业务"，通过 cats-mtl 声明能力：
 *     Ask[F, AppConfig]     —— 读配置（比如 title 最大长度）
 *     Raise[F, TodoError]   —— 业务错误（NotFound / InvalidInput）
 *     Tell[F, List[String]] —— 审计日志（每次写操作累加）
 *
 *   数据访问 repo 通过构造器注入 —— 是 effect 的 IO 动作，
 *   但业务层通过一个 "liftIO: F[A]" 的委托把它 lift 到多态 F 里。
 *
 *   ★ 为什么要这样分？
 *     - Repo 返回 IO 是"边界层"（JDBC 硬依赖）
 *     - Service 对 F 多态 → 便于不同 interpreter（生产/测试）
 *     - 当 F = IO 时，liftIO 就是 identity
 * ============================================================
 */
class TodoService[F[_]: Monad: Clock](
    repo:   TodoRepo,
    liftIO: [A] => IO[A] => F[A]
)(using
    cfg:   Ask[F, AppConfig],
    raise: Raise[F, TodoError],
    tell:  Tell[F, List[String]]
) {

  // 当前时间
  private def now: F[Instant] =
    Clock[F].realTime.map(d => Instant.ofEpochMilli(d.toMillis))

  private def audit(msg: String): F[Unit] = tell.tell(List(msg))

  // ============================================================
  // create
  // ============================================================
  def create(req: CreateTodo): F[Todo] = for {
    c      <- cfg.ask

    // 校验 —— 通过 cats-mtl 短路
    _      <- if (req.title.trim.isEmpty)
                raise.raise(TodoError.InvalidInput("title 不能为空")): F[Unit]
              else if (req.title.length > c.biz.maxTitleLength)
                raise.raise(TodoError.InvalidInput(
                  s"title 超过长度限制(${c.biz.maxTitleLength})")): F[Unit]
              else Monad[F].unit

    t      <- now
    todo   = Todo(
               id        = TodoId.random,
               title     = req.title.trim,
               completed = false,
               createdAt = t,
               updatedAt = t
             )
    _      <- liftIO(repo.insert(todo))
    _      <- audit(s"CREATE ${todo.id.value} \"${todo.title}\"")
  } yield todo

  // ============================================================
  // list
  // ============================================================
  def list: F[List[Todo]] = liftIO(repo.listAll)

  // ============================================================
  // get
  // ============================================================
  def get(id: TodoId): F[Todo] = for {
    opt  <- liftIO(repo.findById(id))
    todo <- opt match {
              case Some(t) => Monad[F].pure(t)
              case None    => raise.raise(TodoError.NotFound(id)): F[Todo]
            }
  } yield todo

  // ============================================================
  // update
  // ============================================================
  def update(id: TodoId, req: UpdateTodo): F[Todo] = for {
    c      <- cfg.ask

    existing <- get(id)  // 复用：找不到会自动 Raise NotFound

    // 应用 patch 并校验
    newTitle =  req.title.map(_.trim).getOrElse(existing.title)
    _        <- if (newTitle.isEmpty)
                  raise.raise(TodoError.InvalidInput("title 不能为空")): F[Unit]
                else if (newTitle.length > c.biz.maxTitleLength)
                  raise.raise(TodoError.InvalidInput(
                    s"title 超过长度限制(${c.biz.maxTitleLength})")): F[Unit]
                else Monad[F].unit

    t        <- now
    updated  =  existing.copy(
                  title     = newTitle,
                  completed = req.completed.getOrElse(existing.completed),
                  updatedAt = t
                )
    _        <- liftIO(repo.updateTodo(updated))
    _        <- audit(s"UPDATE ${id.value} \"${updated.title}\" completed=${updated.completed}")
  } yield updated

  // ============================================================
  // delete
  // ============================================================
  def delete(id: TodoId): F[Unit] = for {
    n <- liftIO(repo.deleteById(id))
    _ <- if (n == 0) raise.raise(TodoError.NotFound(id)): F[Unit]
         else Monad[F].unit
    _ <- audit(s"DELETE ${id.value}")
  } yield ()
}
