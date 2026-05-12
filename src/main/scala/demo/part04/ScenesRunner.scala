package demo.part04

/**
 * 偏函数 8 个应用场景总览 & 运行入口
 *
 * 运行方式：
 *   1. 在 IDEA 中直接右键运行此文件的 main 方法
 *   2. 或者分别运行 Scene01_~Scene08_ 每个文件的 main 方法
 *
 * 8 个场景速览：
 *   Scene01 订单状态机       - isDefinedAt 自动拒绝非法状态
 *   Scene02 日志分级管道      - orElse 组合多个处理器
 *   Scene03 HTTP 路由         - orElse 表达优先级（认证 -> 路由 -> 404）
 *   Scene04 ETL 数据清洗      - collect 同时过滤+转换
 *   Scene05 Actor 消息处理    - Receive = PartialFunction[Any, Unit] + become
 *   Scene06 表单校验          - lift + flatMap 收集所有错误
 *   Scene07 类 Spark 处理     - collect(pf) 在事件流中抽取+聚合
 *   Scene08 Future 异常恢复   - recover 精准捕获已知异常
 */
object ScenesRunner {
  def main(args: Array[String]): Unit = {
    val scenes: List[(String, () => Unit)] = List(
      "场景 1：电商订单状态机"          -> (() => Scene01_OrderStateMachine.main(Array.empty)),
      "场景 2：日志分级处理管道"         -> (() => Scene02_LogPipeline.main(Array.empty)),
      "场景 3：HTTP 请求路由"            -> (() => Scene03_HttpRouter.main(Array.empty)),
      "场景 4：ETL 数据清洗"             -> (() => Scene04_DataCleansing.main(Array.empty)),
      "场景 5：类 Actor 消息处理"        -> (() => Scene05_ActorMessageHandling.main(Array.empty)),
      "场景 6：表单验证器"               -> (() => Scene06_FormValidator.main(Array.empty)),
      "场景 7：类 Spark 数据处理"        -> (() => Scene07_SparkLikeProcessing.main(Array.empty)),
      "场景 8：Future 异常恢复"          -> (() => Scene08_FutureRecovery.main(Array.empty))
    )

    scenes.foreach { case (title, run) =>
      println("\n" + "=" * 60)
      println(s"  $title")
      println("=" * 60)
      run()
    }

    println("\n" + "=" * 60)
    println("  所有场景演示完毕 ✓")
    println("=" * 60)
  }
}
