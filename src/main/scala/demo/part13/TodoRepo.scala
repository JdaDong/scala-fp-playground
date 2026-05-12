package demo.part13

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import doobie.util.transactor.Transactor

import Domain.*

/**
 * ============================================================
 * TodoRepo —— 数据访问层（doobie）
 *
 *   doobie 三板斧：
 *     1) sql"..." 构造 SQL 片段，自动处理参数化
 *     2) .query[T] / .update 得到 Query0 / Update0
 *     3) .to[List] / .option / .run 得到 ConnectionIO
 *     4) .transact(xa) 翻译成 IO
 *
 *   ★ 实现细节：
 *     - UUID 用 VARCHAR(36) 存（H2 对 UUID 列有原生支持但 doobie 默认 Put 缺省，换 String 最稳）
 *     - 用 TodoRow case class 做 DB ↔ Domain 的映射，避免大 Tuple 推断问题
 * ============================================================
 */
trait TodoRepo {
  def initSchema:                     IO[Unit]
  def insert(todo: Todo):             IO[Unit]
  def findById(id: TodoId):           IO[Option[Todo]]
  def listAll:                        IO[List[Todo]]
  def updateTodo(todo: Todo):         IO[Int]
  def deleteById(id: TodoId):         IO[Int]
}

object TodoRepo {

  // 数据库行映射
  private final case class TodoRow(
    id:         String,
    title:      String,
    completed:  Boolean,
    createdAt:  Instant,
    updatedAt:  Instant
  ) {
    def toDomain: Todo = Todo(
      id        = TodoId(UUID.fromString(id)),
      title     = title,
      completed = completed,
      createdAt = createdAt,
      updatedAt = updatedAt
    )
  }

  private def toRow(t: Todo): TodoRow = TodoRow(
    id        = t.id.value.toString,
    title     = t.title,
    completed = t.completed,
    createdAt = t.createdAt,
    updatedAt = t.updatedAt
  )

  def apply(xa: Transactor[IO]): TodoRepo = new TodoRepo {

    def initSchema: IO[Unit] =
      sql"""
        CREATE TABLE IF NOT EXISTS todos (
          id          VARCHAR(36)  PRIMARY KEY,
          title       VARCHAR(256) NOT NULL,
          completed   BOOLEAN      NOT NULL,
          created_at  TIMESTAMP    NOT NULL,
          updated_at  TIMESTAMP    NOT NULL
        )
      """.update.run.transact(xa).void

    def insert(t: Todo): IO[Unit] = {
      val r = toRow(t)
      sql"""
        INSERT INTO todos (id, title, completed, created_at, updated_at)
        VALUES (${r.id}, ${r.title}, ${r.completed}, ${r.createdAt}, ${r.updatedAt})
      """.update.run.transact(xa).void
    }

    def findById(id: TodoId): IO[Option[Todo]] = {
      val key = id.value.toString
      sql"""
        SELECT id, title, completed, created_at, updated_at
        FROM todos WHERE id = $key
      """.query[TodoRow].option.map(_.map(_.toDomain)).transact(xa)
    }

    def listAll: IO[List[Todo]] =
      sql"""
        SELECT id, title, completed, created_at, updated_at
        FROM todos
        ORDER BY created_at DESC
      """.query[TodoRow].to[List].map(_.map(_.toDomain)).transact(xa)

    def updateTodo(t: Todo): IO[Int] = {
      val r = toRow(t)
      sql"""
        UPDATE todos SET
          title      = ${r.title},
          completed  = ${r.completed},
          updated_at = ${r.updatedAt}
        WHERE id = ${r.id}
      """.update.run.transact(xa)
    }

    def deleteById(id: TodoId): IO[Int] = {
      val key = id.value.toString
      sql"DELETE FROM todos WHERE id = $key".update.run.transact(xa)
    }
  }
}
