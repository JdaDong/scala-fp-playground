package demo.part10

import cats.effect.{IO, IOApp}
import fs2.Stream
import scala.concurrent.duration.*

/**
 * ============================================================
 * Scene 02: 反压（backpressure）—— fs2 解决"生产快、消费慢"的核武器
 *
 *   场景：一个生产者每 50ms 出一条数据，消费者每 200ms 才能处理一条。
 *
 *   传统线程方案：
 *     - 用阻塞队列 + 显式同步，写起来一团糟，容易死锁/OOM
 *
 *   fs2 方案：
 *     - 链式表达：source.metered(50.millis).evalMap(slow)
 *     - 反压自动：当 evalMap 还没处理完，source 自动暂停发射
 *     - 不需要写一行同步代码
 *
 *   本场景演示 4 种"快慢匹配"模式：
 *     1) 串行（默认）        —— 最慢，但顺序保证
 *     2) 并行 parEvalMap     —— 多线程消费，提速
 *     3) 缓冲 buffer          —— 给消费者一个缓冲区
 *     4) 限流 metered         —— 主动限制生产速度
 * ============================================================
 */
object Scene02_FS2Backpressure extends IOApp.Simple {

  // 一个慢的"消费"操作
  def slowProcess(label: String)(n: Int): IO[Int] =
    IO.sleep(150.millis) *> IO.println(s"  [$label] processed $n").as(n)

  // ============================================================
  // 1) 串行（默认）：每秒大约 6 条
  // ============================================================
  def demo_serial: IO[Unit] = for {
    _   <- IO.println("\n===== 1. 串行 evalMap =====")
    t0  <- IO.monotonic
    _   <- Stream.range(1, 6)
            .evalMap(slowProcess("serial"))
            .compile.drain
    t1  <- IO.monotonic
    _   <- IO.println(s"  耗时：${(t1 - t0).toMillis} ms")
  } yield ()

  // ============================================================
  // 2) 并行 parEvalMap(maxConcurrent)：4 个 worker 同时消费
  // ============================================================
  def demo_parallel: IO[Unit] = for {
    _   <- IO.println("\n===== 2. 并行 parEvalMap(4) =====")
    t0  <- IO.monotonic
    _   <- Stream.range(1, 6)
            .parEvalMap(4)(slowProcess("parallel"))
            .compile.drain
    t1  <- IO.monotonic
    _   <- IO.println(s"  耗时：${(t1 - t0).toMillis} ms （并行 → 大大缩短）")
  } yield ()

  // ============================================================
  // 3) 缓冲 buffer(N)：异步缓冲，平滑处理
  // ============================================================
  def demo_buffer: IO[Unit] = for {
    _   <- IO.println("\n===== 3. buffer(3)：异步缓冲 =====")
    _   <- Stream.range(1, 6).covary[IO]
            // 生产端：每 30ms 一条
            .metered(30.millis)
            .buffer(3)
            // 消费端：慢，每 100ms 一条
            .evalMap(n => IO.sleep(100.millis) *> IO.println(s"  consumed $n"))
            .compile.drain
  } yield ()

  // ============================================================
  // 4) metered：主动限制速率（rate limiting）
  //    例：API 调用每秒不超过 5 次
  // ============================================================
  def demo_metered: IO[Unit] = for {
    _   <- IO.println("\n===== 4. metered(200ms)：主动限速 =====")
    t0  <- IO.monotonic
    _   <- Stream.range(1, 6).covary[IO]
            .metered(200.millis)
            .evalMap(n => IO.println(s"  call API #$n"))
            .compile.drain
    t1  <- IO.monotonic
    _   <- IO.println(s"  耗时：${(t1 - t0).toMillis} ms （≈ 4 * 200 = 800ms）")
  } yield ()

  override def run: IO[Unit] = for {
    _ <- IO.println("===== Scene02: 反压与流量控制 =====")
    _ <- demo_serial
    _ <- demo_parallel
    _ <- demo_buffer
    _ <- demo_metered
    _ <- IO.println(
          """
            |  ★ 关键观察：
            |    1. fs2 把"快慢匹配"用组合子表达：parEvalMap / buffer / metered
            |    2. 整个流水线"自动反压"：消费慢自然暂停生产
            |    3. 不需要 BlockingQueue / Semaphore / synchronized
            |    4. 这就是 Kafka 消费者、爬虫调度、API 网关的标准写法
            |""".stripMargin)
    _ <- IO.println("===== Scene02 完成 =====")
  } yield ()
}
