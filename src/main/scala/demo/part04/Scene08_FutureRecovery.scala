package demo.part04

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

/**
 * 场景八：异常处理的精细化（Future.recover）
 *
 * 【业务背景】
 *   调用外部服务的 Future 可能因多种原因失败：
 *     - 网络超时 -> 可以返回缓存值
 *     - 资源未找到 -> 可以返回默认值
 *     - 鉴权失败 -> 需要上抛给认证模块
 *     - 未知异常 -> 不应该吞掉，必须继续传播
 *   我们只想捕获"我认识的异常"，其他异常原样抛出。
 *
 * 【偏函数的价值】
 *   - Future.recover 和 recoverWith 接受的就是 PartialFunction[Throwable, T]
 *   - 未在 case 中出现的异常类型 -> "未定义" -> 不被捕获，继续传播
 *   - 这比 try-catch 捕获 Exception 再判断更安全，避免"吞掉所有异常"的反模式
 */
object Scene08_FutureRecovery {

  // 自定义异常
  class TimeoutExceptionX(msg: String)     extends RuntimeException(msg)
  class NotFoundExceptionX(msg: String)    extends RuntimeException(msg)
  class UnauthorizedExceptionX(msg: String) extends RuntimeException(msg)

  case class User(name: String, source: String)

  // 模拟远程调用
  def fetchUser(id: String): Future[User] = Future {
    id match {
      case "1" => User("Alice", "remote")
      case "2" => throw new TimeoutExceptionX("网络超时")
      case "3" => throw new NotFoundExceptionX("用户不存在")
      case "4" => throw new UnauthorizedExceptionX("未授权")
      case _   => throw new RuntimeException("未知错误")  // 未知异常
    }
  }

  // 恢复策略：只处理我认识的异常，其他原样传播
  val recoveryStrategy: PartialFunction[Throwable, User] = {
    case _: TimeoutExceptionX  => User("默认用户(超时)",   "cache")
    case _: NotFoundExceptionX => User("默认用户(未找到)", "default")
    // 注意：UnauthorizedException 和 RuntimeException 都没被处理 -> 继续抛出
  }

  def fetchUserSafe(id: String): Future[User] =
    fetchUser(id).recover(recoveryStrategy)

  def main(args: Array[String]): Unit = {
    println("=== 场景八：Future 精细化异常恢复 ===\n")

    val testIds = List("1", "2", "3", "4", "5")

    testIds.foreach { id =>
      val result = Try(Await.result(fetchUserSafe(id), 2.seconds))
      result match {
        case Success(user) => println(f"id=$id%s => ✓ 成功: $user")
        case Failure(err)  => println(f"id=$id%s => ✗ 未捕获异常: ${err.getClass.getSimpleName}: ${err.getMessage}")
      }
    }

    println("\n--- 对比：traditional try-catch 的陷阱 ---")
    // 反面教材：吞掉所有异常
    def fetchUserBad(id: String): Future[User] =
      fetchUser(id).recover { case _: Throwable => User("兜底", "fallback") }

    // 后果：连 Unauthorized 和未知异常都被吞了，问题被掩盖
    testIds.foreach { id =>
      val r = Await.result(fetchUserBad(id), 2.seconds)
      println(s"id=$id => $r  <-- 问题全被吞掉！")
    }
  }
}
