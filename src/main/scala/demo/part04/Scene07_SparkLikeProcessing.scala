package demo.part04

/**
 * 场景七：类 Spark 数据处理（大数据）
 *
 * 【业务背景】
 *   Spark/Flink 等大数据框架中，一个 RDD/DataSet 可能包含多种类型的元素，
 *   常见需求：
 *     - 只抽取某一类型（比如只处理整数，忽略字符串）
 *     - 同时做过滤 + 类型转换（比如从日志行提取数字）
 *
 * 【偏函数的价值】
 *   - Spark 的 RDD.collect(pf) / DataSet 的很多 API 都直接接受偏函数
 *   - 把"过滤条件 + 转换逻辑"统一成一个表达式，表达力和可读性都更好
 *   - 本例用本地 List 模拟 Spark 的分布式集合操作
 */
object Scene07_SparkLikeProcessing {

  // 模拟 RDD 的简化类型
  case class FakeRDD[T](data: List[T]) {
    def collectPF[U](pf: PartialFunction[T, U]): FakeRDD[U] = FakeRDD(data.collect(pf))
    def filterPF(pf: PartialFunction[T, Boolean]): FakeRDD[T] =
      FakeRDD(data.filter(pf.applyOrElse(_, (_: T) => false)))
    def show(): Unit = data.foreach(x => println(s"   $x"))
  }

  // 示例业务数据（模拟日志 + 指标混合）
  sealed trait Event
  case class UserLogin(userId: Int, ts: Long)        extends Event
  case class Purchase(userId: Int, amount: Double)   extends Event
  case class ErrorLog(msg: String)                   extends Event
  case class Heartbeat(server: String)               extends Event

  def main(args: Array[String]): Unit = {
    println("=== 场景七：类 Spark 数据处理 ===\n")

    // --- 例 1：混合类型数据中只抽取整数并平方 ---
    val mixedRDD = FakeRDD[Any](List("hello", 42, 3.14, "world", 100, 7))
    println("【例1】从混合数据中抽取整数并平方：")
    val squared = mixedRDD.collectPF { case i: Int => i * i }
    squared.show()

    // --- 例 2：从事件流中抽取购买金额并求总和 ---
    val events = FakeRDD(List[Event](
      UserLogin(1, 1000L),
      Purchase(1, 99.9),
      ErrorLog("磁盘满"),
      Purchase(2, 150.0),
      Heartbeat("srv-01"),
      Purchase(1, 20.5),
      UserLogin(3, 2000L)
    ))

    println("\n【例2】从事件流中抽取所有购买金额：")
    val amounts = events.collectPF { case Purchase(_, amt) => amt }
    amounts.show()
    println(s"   => 总金额: ${amounts.data.sum}")

    // --- 例 3：用户级聚合（按用户 ID 统计购买总额）---
    println("\n【例3】按用户聚合购买总额：")
    val userSpending = events.data
      .collect { case Purchase(uid, amt) => uid -> amt }  // 同时过滤 + 转换
      .groupBy(_._1)
      .view.mapValues(_.map(_._2).sum)
      .toMap
    userSpending.foreach { case (uid, total) =>
      println(s"   用户 $uid 消费总额: $total")
    }

    // --- 例 4：错误信息提取（偏函数复用）---
    println("\n【例4】提取所有错误日志：")
    val errorExtractor: PartialFunction[Event, String] = {
      case ErrorLog(msg) => s"[ERR] $msg"
    }
    events.collectPF(errorExtractor).show()
  }
}
