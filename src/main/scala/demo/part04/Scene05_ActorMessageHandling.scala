package demo.part04

/**
 * 场景五：类 Actor 消息处理系统（模拟 Akka）
 *
 * 【业务背景】
 *   Actor 模型中，每个 Actor 接收各种类型的消息，它只处理自己关心的消息，
 *   不认识的消息应该被记录或转发。典型需求：
 *     - 一个 Actor 的行为由多个"职责模块"组成（订单处理、支付处理、未知兜底）
 *     - 运行时可以切换 Actor 的行为（如从"正常模式"切换到"维护模式"）
 *
 * 【偏函数的价值】
 *   - Akka 的 Receive 类型就是 PartialFunction[Any, Unit]
 *   - 多个职责模块可通过 orElse 合并，物理上拆分到不同文件也没问题
 *   - 行为切换只需切换偏函数引用（本例用 become 模拟 context.become）
 */
object Scene05_ActorMessageHandling {

  // 消息定义
  sealed trait Message
  case class CreateOrder(id: String)           extends Message
  case class QueryOrder(id: String)            extends Message
  case class PaymentSuccess(orderId: String)   extends Message
  case object EnterMaintenance                 extends Message
  case object ExitMaintenance                  extends Message

  // 类型别名，模拟 Akka 的 Receive
  type Receive = PartialFunction[Any, Unit]

  // 简化版 Actor
  class OrderActor {
    private var orders = Map.empty[String, String]  // orderId -> status

    // 订单处理模块（用 lazy val 避免初始化顺序陷阱：
    //   currentBehavior 早于普通 val 求值时会拿到 null）
    lazy val handleOrder: Receive = {
      case CreateOrder(id) =>
        orders += (id -> "Created")
        println(s"   [订单] 已创建 $id")
      case QueryOrder(id) =>
        println(s"   [订单] 查询 $id => ${orders.getOrElse(id, "不存在")}")
    }

    // 支付处理模块
    lazy val handlePayment: Receive = {
      case PaymentSuccess(orderId) =>
        orders += (orderId -> "Paid")
        println(s"   [支付] 订单 $orderId 已支付")
    }

    // 行为切换模块
    lazy val handleSwitch: Receive = {
      case EnterMaintenance =>
        println("   [切换] 进入维护模式")
        currentBehavior = maintenanceBehavior
      case ExitMaintenance =>
        println("   [切换] 退出维护模式")
        currentBehavior = normalBehavior
    }

    // 未知消息兜底
    lazy val handleUnknown: Receive = {
      case msg => println(s"   [警告] 未知消息: $msg")
    }

    // 正常模式：所有模块都接收
    def normalBehavior: Receive =
      handleOrder orElse handlePayment orElse handleSwitch orElse handleUnknown

    // 维护模式：只接收切换命令，其他全部拒绝
    def maintenanceBehavior: Receive = handleSwitch orElse {
      case msg => println(s"   [维护中] 拒绝处理: $msg")
    }

    // currentBehavior 必须在 lazy val 之后声明，
    // 这样首次访问 normalBehavior 时所有 lazy val 才会被触发
    private var currentBehavior: Receive = normalBehavior

    // 接收并处理消息
    def send(msg: Any): Unit = {
      println(s">> 发送: $msg")
      currentBehavior(msg)
    }
  }

  def main(args: Array[String]): Unit = {
    println("=== 场景五：类 Actor 消息处理 ===\n")

    val actor = new OrderActor

    // 正常模式下处理
    actor.send(CreateOrder("ORD-001"))
    actor.send(PaymentSuccess("ORD-001"))
    actor.send(QueryOrder("ORD-001"))
    actor.send("未知消息")

    // 切换到维护模式
    actor.send(EnterMaintenance)
    actor.send(CreateOrder("ORD-002"))   // 会被拒绝
    actor.send(QueryOrder("ORD-001"))    // 会被拒绝

    // 切换回正常模式
    actor.send(ExitMaintenance)
    actor.send(CreateOrder("ORD-002"))   // 恢复正常处理
  }
}
