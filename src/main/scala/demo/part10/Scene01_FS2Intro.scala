package demo.part10

import cats.effect.{IO, IOApp}
import fs2.Stream
import scala.concurrent.duration.*

/**
 * ============================================================
 * Scene 01: fs2 Stream 入门 —— 真正"惰性、可组合、有效果"的流
 *
 *   fs2 Stream 与普通 List/Iterator 的区别：
 *     - List      : 立刻装入内存，无法表达"无限流"
 *     - Iterator  : 惰性但不可组合，无法表达"异步/并发"
 *     - Stream[F, A]:
 *        ✅ 惰性求值
 *        ✅ 元素产生过程可以是 effect（如 IO 读文件、HTTP 调用）
 *        ✅ 支持 take / map / filter / flatMap / parEvalMap / 错误恢复
 *        ✅ 支持反压（Scene02）
 *        ✅ 支持取消和资源安全（与 cats-effect 集成）
 *
 *   ★ Stream[F, A] 几乎可以理解为：
 *     一个"生产 A 的、副作用是 F 的"懒序列
 * ============================================================
 */
object Scene01_FS2Intro extends IOApp.Simple {

  // ============================================================
  // ① 构造 Stream 的 N 种方式
  // ============================================================
  val s1: Stream[IO, Int]    = Stream(1, 2, 3, 4, 5)             // 静态元素
  val s2: Stream[IO, Int]    = Stream.range(1, 11)               // 范围（不含尾）
  val s3: Stream[IO, String] = Stream.emits(List("a", "b", "c")) // 从集合
  val nat: Stream[IO, Int]   = Stream.iterate(1)(_ + 1)          // 无限流：1,2,3,...

  def demo_basics: IO[Unit] = for {
    _  <- IO.println("===== 1. 构造 Stream =====")

    xs1 <- s1.compile.toList
    _   <- IO.println(s"  Stream(1..5)        → $xs1")

    xs2 <- s2.compile.toList
    _   <- IO.println(s"  range(1, 11)        → $xs2")

    xs3 <- s3.compile.toList
    _   <- IO.println(s"  emits(List(...))    → $xs3")

    // ★ 关键：无限流也能用 take 截取（List 永远做不到）
    xsN <- nat.take(5).compile.toList
    _   <- IO.println(s"  无限流 take(5)       → $xsN")
  } yield ()

  // ============================================================
  // ② 转换：map / filter / flatMap / scan
  // ============================================================
  def demo_transform: IO[Unit] = for {
    _ <- IO.println("\n===== 2. 转换 =====")

    a <- s2.map(_ * 10).filter(_ > 30).compile.toList
    _ <- IO.println(s"  range * 10 filter>30 → $a")

    b <- Stream(1, 2, 3).covary[IO].flatMap(n => Stream.range(0, n)).compile.toList
    _ <- IO.println(s"  flatMap：扁平        → $b")

    c <- nat.take(5).scan(0)(_ + _).compile.toList
    _ <- IO.println(s"  累积和 scan(0)(_+_) → $c")

    d <- nat.take(100).foldMonoid.compile.toList
    _ <- IO.println(s"  1+2+..+100         → $d")
  } yield ()

  // ============================================================
  // ③ 副作用：evalMap —— 在每个元素上执行 effect
  // ============================================================
  def demo_eval: IO[Unit] = for {
    _ <- IO.println("\n===== 3. evalMap：每个元素执行 effect =====")
    _ <- Stream.range(1, 4)
           .evalMap(n => IO.println(s"  处理元素 $n").as(n * 100))
           .compile.toList
           .flatMap(xs => IO.println(s"  最终结果：$xs"))
  } yield ()

  // ============================================================
  // ④ compile.* —— 收尾的多种方式
  // ============================================================
  def demo_compile: IO[Unit] = {
    val s = Stream.range(1, 11).covary[IO]
    for {
      _   <- IO.println("\n===== 4. compile 收尾 =====")
      xs  <- s.compile.toList
      _   <- IO.println(s"  toList      → $xs")
      cnt <- s.compile.count
      _   <- IO.println(s"  count       → $cnt")
      sum <- s.compile.foldMonoid
      _   <- IO.println(s"  foldMonoid  → $sum")
      _   <- s.evalMap(n => IO.print(s"$n ")).compile.drain
      _   <- IO.println(s"\n  drain（只跑副作用，不收集）")
    } yield ()
  }

  override def run: IO[Unit] = for {
    _ <- demo_basics
    _ <- demo_transform
    _ <- demo_eval
    _ <- demo_compile
    _ <- IO.println(
           """
             |  ★ 关键观察：
             |    1. Stream(1, 2, 3) 看起来像 List，但本质是"懒"的
             |    2. 无限流（iterate）也能 take —— List 不行
             |    3. evalMap 让 effect 自然嵌入流，无需 flatMap 嵌套地狱
             |    4. compile.toList / count / drain 是把流"折叠"成结果
             |""".stripMargin)
    _ <- IO.println("===== Scene01 完成 =====")
  } yield ()
}
