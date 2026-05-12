package demo.part04

/**
 * 场景二：日志分级处理管道
 *
 * 【业务背景】
 *   系统产生的日志需要按级别做不同处理：
 *     - ERROR 级别：发送告警 + 写入错误日志文件
 *     - WARN  级别：写入警告日志文件
 *     - INFO  级别：打印到控制台
 *     - DEBUG 级别：只在开发环境保留
 *     - 未识别的级别：兜底处理，避免信息丢失
 *   各个处理器应该解耦、可独立测试、可动态组合。
 *
 * 【偏函数的价值】
 *   - 每个处理器是独立的偏函数，职责单一
 *   - orElse 运算符优雅地表达"按优先级尝试"的处理链
 *   - 运行时可根据环境动态增删处理器（如生产环境关闭 DEBUG 处理器）
 */
object Scene02_LogPipeline {

  case class LogEntry(level: String, message: String, timestamp: Long)

  // 各级日志处理器独立定义
  val errorHandler: PartialFunction[LogEntry, Unit] = {
    case LogEntry("ERROR", msg, ts) =>
      sendAlert(msg)
      writeToFile("error.log", s"[$ts] $msg")
  }

  val warnHandler: PartialFunction[LogEntry, Unit] = {
    case LogEntry("WARN", msg, ts) =>
      writeToFile("warn.log", s"[$ts] $msg")
  }

  val infoHandler: PartialFunction[LogEntry, Unit] = {
    case LogEntry("INFO", msg, _) =>
      println(s"[INFO] $msg")
  }

  val debugHandler: PartialFunction[LogEntry, Unit] = {
    case LogEntry("DEBUG", msg, _) =>
      println(s"[DEBUG] $msg")
  }

  // 兜底处理器
  val defaultHandler: PartialFunction[LogEntry, Unit] = {
    case entry =>
      println(s"[未知级别:${entry.level}] ${entry.message}")
  }

  // 辅助方法
  def sendAlert(msg: String): Unit         = println(s"   >> ALERT 邮件/短信: $msg")
  def writeToFile(f: String, m: String): Unit = println(s"   >> 写入 $f : $m")

  def main(args: Array[String]): Unit = {
    println("=== 场景二：日志分级处理管道 ===\n")

    // 场景 A：生产环境管道（不开启 DEBUG）
    val prodPipeline = errorHandler orElse warnHandler orElse infoHandler orElse defaultHandler

    // 场景 B：开发环境管道（开启 DEBUG）
    val devPipeline =
      errorHandler orElse warnHandler orElse infoHandler orElse debugHandler orElse defaultHandler

    val logs = List(
      LogEntry("ERROR", "数据库连接失败", 1000L),
      LogEntry("WARN",  "磁盘使用率超过 80%", 2000L),
      LogEntry("INFO",  "用户 Alice 登录", 3000L),
      LogEntry("DEBUG", "缓存命中 key=user_42", 4000L),
      LogEntry("TRACE", "函数进入 foo()", 5000L) // 未知级别
    )

    println("--- 生产环境管道 ---")
    logs.foreach(prodPipeline)

    println("\n--- 开发环境管道 ---")
    logs.foreach(devPipeline)
  }
}
