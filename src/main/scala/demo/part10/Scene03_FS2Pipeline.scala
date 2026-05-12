package demo.part10

import cats.effect.{IO, IOApp, Ref}
import fs2.{Pipe, Stream}
import scala.concurrent.duration.*

/**
 * ============================================================
 * Scene 03: Pipe 与流水线 —— 让流处理变成"管道拼接"
 *
 *   Pipe[F, A, B] = Stream[F, A] => Stream[F, B]
 *     即一个"流变流"的函数。
 *
 *   ★ Pipe 是 fs2 最优雅的抽象：
 *     - 业务作者写一堆 Pipe（解码、过滤、聚合、统计...）
 *     - 上层用 .through(pipe) 串起来
 *     - 整个数据处理变成"乐高积木"
 *
 *   场景：处理一份"日志流"
 *     原始 → parseLine → filterError → enrichWithTime → group → metrics
 * ============================================================
 */
object Scene03_FS2Pipeline extends IOApp.Simple {

  // ============================================================
  // 业务模型
  // ============================================================
  case class LogLine(level: String, msg: String, ts: Long = 0L)
  case class Metrics(total: Int, errors: Int, warns: Int)

  // ============================================================
  // 模拟日志源
  // ============================================================
  val rawLogs: Stream[IO, String] = Stream.emits(List(
    "INFO   user=42 login",
    "ERROR  db timeout",
    "WARN   slow query 1.2s",
    "INFO   user=43 logout",
    "ERROR  cache miss",
    "garbage line",          // 故意一条非法
    "WARN   gc paused 200ms",
    "INFO   ping ok"
  ))

  // ============================================================
  // ① Pipe: 字符串 → LogLine（解析）
  // ============================================================
  val parse: Pipe[IO, String, LogLine] = _.flatMap { line =>
    line.split("\\s+", 2).toList match {
      case lvl :: rest :: Nil if Set("INFO", "WARN", "ERROR").contains(lvl) =>
        Stream.emit(LogLine(lvl, rest))
      case _ =>
        Stream.empty   // 非法行直接丢弃（或可写到 dead-letter pipe）
    }
  }

  // ============================================================
  // ② Pipe: 仅保留 ERROR / WARN
  // ============================================================
  val onlyAlerts: Pipe[IO, LogLine, LogLine] =
    _.filter(l => l.level == "ERROR" || l.level == "WARN")

  // ============================================================
  // ③ Pipe: 用真实时间打戳（带 effect）
  // ============================================================
  val stampTime: Pipe[IO, LogLine, LogLine] = _.evalMap { l =>
    IO.realTime.map(d => l.copy(ts = d.toMillis))
  }

  // ============================================================
  // ④ Pipe: 累计统计 → Metrics 流
  //    用 mapAccumulate 在保持流式的同时累计状态
  // ============================================================
  val metrics: Pipe[IO, LogLine, Metrics] = _.scan(Metrics(0, 0, 0)) { (acc, l) =>
    val errors = if (l.level == "ERROR") acc.errors + 1 else acc.errors
    val warns  = if (l.level == "WARN")  acc.warns  + 1 else acc.warns
    Metrics(acc.total + 1, errors, warns)
  }

  // ============================================================
  // ⑤ 组合：通过 .through 串成完整流水线
  // ============================================================
  override def run: IO[Unit] = for {
    _ <- IO.println("===== Scene03: Pipe 流水线 =====\n")

    _ <- IO.println("【1】解析 + 时间戳：所有日志打齐")
    _ <- rawLogs
           .through(parse)
           .through(stampTime)
           .evalMap(l => IO.println(s"    $l"))
           .compile.drain

    _ <- IO.println("\n【2】只保留 ERROR / WARN")
    _ <- rawLogs
           .through(parse)
           .through(onlyAlerts)
           .evalMap(l => IO.println(s"    🚨 $l"))
           .compile.drain

    _ <- IO.println("\n【3】实时累计指标（每条日志都吐一次 metrics）")
    _ <- rawLogs
           .through(parse)
           .through(metrics)
           .evalMap(m => IO.println(s"    $m"))
           .compile.drain

    _ <- IO.println(
           """
             |  ★ 关键观察：
             |    1. 每个 Pipe 是一个独立、可测试、可复用的小函数
             |    2. .through(pipe) 让流水线写起来像 Unix pipe（cat | grep | wc）
             |    3. 同一个原始流，用不同 Pipe 组合就能得到完全不同的结果
             |    4. 这就是 Spark Dataset / Akka Stream graph 等的"小型工业版"
             |""".stripMargin)
    _ <- IO.println("===== Scene03 完成 =====")
  } yield ()
}
