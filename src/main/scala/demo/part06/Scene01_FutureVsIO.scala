package demo.part06

import cats.effect.{IO, IOApp, ExitCode}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.*

/**
 * ============================================================
 * Scene 01: Future vs IO —— 本质差异
 *
 *   一句话总结：
 *     Future = "立刻执行" 的副作用容器（eager，副作用即定即发）
 *     IO     = "执行计划"  的副作用描述（lazy，构造时不执行，run 时才执行）
 *
 *   差异带来的能力：
 *     1) 引用透明（Referential Transparency）
 *        ── IO 的 val x = ...，无论用多少次都等价于把表达式贴回去
 *        ── Future 不行：val 一旦定义就开始执行
 *     2) 可重试 / 可取消 / 可组合
 *     3) 错误是值（不会"消失"）
 * ============================================================
 */
object Scene01_FutureVsIO extends IOApp {

  // ============================================================
  // 1. Future 的"陷阱"：定义即执行 → 不可重试、副作用泄漏
  // ============================================================
  def demo_FutureIsEager(): Unit = {
    println("===== 1. Future：定义即执行 =====")
    given ExecutionContext = ExecutionContext.global

    // 这个 Future 在 val 赋值的那一刻就已经开始执行了
    val printHello: Future[Unit] = Future {
      println("    [Future] hello! (执行时机不可控)")
    }

    // 即使你"用"它两次，副作用也只发生 1 次
    val combined = for {
      _ <- printHello
      _ <- printHello   // ← 不会再打印一次！
    } yield ()

    Thread.sleep(200)
    println("    （只看到 1 次 hello，因为 Future 不是值，是已启动的任务）\n")
  }

  // ============================================================
  // 2. IO 的"懒"：定义不执行 → 可重复使用、可重试
  // ============================================================
  val demo_IOIsLazy: IO[Unit] = for {
    _ <- IO.println("===== 2. IO：定义不执行，描述一个程序 =====")
    printHello = IO.println("    [IO] hello!")  // 注意：这里仅"描述"，没有任何副作用发生
    _ <- IO.println("    （IO 已被定义，但还没执行）")
    _ <- printHello                              // 第 1 次执行
    _ <- printHello                              // 第 2 次执行 ← 副作用真的发生了 2 次
    _ <- IO.println("    （看到 2 次 hello，IO 是真正的『值』）\n")
  } yield ()

  // ============================================================
  // 3. 引用透明性对比
  //    IO 满足代换原理：把 val 替换成它的右值，程序行为不变
  // ============================================================
  val demo_ReferentialTransparency: IO[Unit] = {
    val tick: IO[Unit] = IO.println("    tick")

    val planA: IO[Unit] =
      tick *> tick *> tick                 // 用 val 引用 3 次

    val planB: IO[Unit] =
      IO.println("    tick") *>
      IO.println("    tick") *>
      IO.println("    tick")               // 把表达式"贴"回 3 次

    for {
      _ <- IO.println("===== 3. 引用透明：planA == planB =====")
      _ <- IO.println("    -- planA --")
      _ <- planA
      _ <- IO.println("    -- planB --")
      _ <- planB
      _ <- IO.println("    （行为完全一致 → IO 是值）\n")
    } yield ()
  }

  // ============================================================
  // 4. 错误处理：IO 错误是值，不会"漏掉"
  // ============================================================
  val demo_ErrorAsValue: IO[Unit] = {
    val boom: IO[Int] = IO.raiseError(new RuntimeException("💥 boom"))

    val recovered: IO[Int] = boom.handleErrorWith { e =>
      IO.println(s"    捕获异常：${e.getMessage}").as(-1)
    }

    val attempted: IO[Either[Throwable, Int]] = boom.attempt
    //   把异常变成 Either —— 像处理普通值一样处理错误

    for {
      _   <- IO.println("===== 4. 错误是值：handleErrorWith / attempt =====")
      v1  <- recovered
      _   <- IO.println(s"    handleErrorWith 结果：$v1")
      v2  <- attempted
      _   <- IO.println(s"    attempt 结果：$v2\n")
    } yield ()
  }

  // ============================================================
  // 入口（IOApp 帮你管 ExecutionContext / Runtime / 关闭）
  // ============================================================
  def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- IO(demo_FutureIsEager())          // 用 IO 包一下普通副作用
      _ <- demo_IOIsLazy
      _ <- demo_ReferentialTransparency
      _ <- demo_ErrorAsValue
      _ <- IO.println("===== Scene01 完成 =====")
    } yield ExitCode.Success
}
