package demo.part10

import cats.effect.{IO, IOApp, Ref}
import cats.effect.std.Queue
import fs2.Stream
import scala.concurrent.duration.*

/**
 * ============================================================
 * Scene 04: 实战 —— 多 Producer / 多 Consumer 实时处理系统
 *
 *   场景：搭一个"事件总线"
 *     - 3 个 producer（不同来源）以不同速率发事件
 *     - 1 个事件队列做缓冲
 *     - N 个 consumer 并行处理
 *     - 一个 stats 流实时打印吞吐量
 *
 *   涉及 fs2 + cats-effect 关键能力：
 *     - Queue (cats-effect std)：异步队列
 *     - Stream.fromQueueUnterminated：从 queue 出"流"
 *     - .merge / .parJoin：多流合并
 *     - .interruptAfter：定时停止整个系统（生产里通常是信号触发）
 *     - Ref：共享计数器（统计）
 *
 *   ★ 这就是一个"小型 Kafka 消费者集群"的最简模型
 * ============================================================
 */
object Scene04_FS2EventBus extends IOApp.Simple {

  case class Event(producer: String, payload: Int, ts: Long)

  // ============================================================
  // ① 一个 producer：每 interval 发一条事件到 queue
  // ============================================================
  def producer(name: String, interval: FiniteDuration, q: Queue[IO, Event]): Stream[IO, Unit] =
    Stream.iterate(0)(_ + 1)
      .covary[IO]
      .metered(interval)
      .evalMap { n =>
        for {
          ts <- IO.monotonic
          ev =  Event(name, n, ts.toMillis)
          _  <- q.offer(ev)
        } yield ()
      }

  // ============================================================
  // ② consumer：从 queue 拿事件并处理（带计数器）
  // ============================================================
  def consumer(id: Int, q: Queue[IO, Event], counter: Ref[IO, Int]): Stream[IO, Unit] =
    Stream.fromQueueUnterminated(q)
      .evalMap { ev =>
        for {
          // 模拟处理耗时
          _ <- IO.sleep(60.millis)
          c <- counter.updateAndGet(_ + 1)
          _ <- IO.println(s"  [consumer-$id] processed $ev (total=$c)")
        } yield ()
      }

  // ============================================================
  // ③ stats：每秒打印一次吞吐量
  // ============================================================
  def statsReporter(counter: Ref[IO, Int]): Stream[IO, Unit] =
    Stream.awakeEvery[IO](1.second).evalMap { d =>
      counter.get.flatMap(c => IO.println(s"  ▶ [stats] elapsed=${d.toSeconds}s total=$c"))
    }

  // ============================================================
  // ④ 装配整个系统
  // ============================================================
  override def run: IO[Unit] = for {
    _       <- IO.println("===== Scene04: 多 Producer / 多 Consumer 系统 =====\n")
    q       <- Queue.bounded[IO, Event](capacity = 32)
    counter <- Ref.of[IO, Int](0)

    // 3 个 producer：不同速率
    producers = List(
      producer("P-A", 80.millis,  q),
      producer("P-B", 120.millis, q),
      producer("P-C", 200.millis, q)
    )

    // 4 个 consumer
    consumers = (1 to 4).map(id => consumer(id, q, counter)).toList

    // 用 parJoinUnbounded 把所有流并行运行
    allStreams = Stream.emits(producers ++ consumers ++ List(statsReporter(counter)))
                       .covary[IO]
                       .parJoinUnbounded

    // 跑 3 秒后停止
    _ <- allStreams.interruptAfter(3.seconds).compile.drain

    finalCount <- counter.get
    _          <- IO.println(s"\n  ⏹ 停止 总共处理 $finalCount 条事件")
    _          <- IO.println(
                    """
                      |  ★ 关键观察：
                      |    1. Queue + Stream.fromQueueUnterminated → 最简的事件总线
                      |    2. parJoinUnbounded 把任意多个 Stream 并行运行
                      |    3. interruptAfter 优雅地停止整个系统（资源自动释放）
                      |    4. 加新 producer / consumer 只是往 list 里加一项
                      |""".stripMargin)
    _ <- IO.println("===== Scene04 完成 =====")
  } yield ()
}
