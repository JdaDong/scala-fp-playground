package demo.part04

/**
 * 场景一：电商订单状态机
 *
 * 【业务背景】
 *   电商系统中订单有多个状态（创建、已付款、已发货、已送达、已取消）。
 *   状态之间的转换必须严格遵循业务规则：
 *     - 已送达的订单不能再取消
 *     - 未付款的订单不能直接发货
 *     - 已取消的订单不能再做任何操作
 *   非法的状态转换必须被拒绝。
 *
 * 【偏函数的价值】
 *   - 只定义"合法"的状态转换，非法的自动落入"未定义"区
 *   - isDefinedAt 天然提供合法性校验
 *   - 新增状态转换规则只需添加一个 case，无需改动其他代码
 */
object Scene01_OrderStateMachine {

  // 订单状态
  sealed trait OrderState
  case object Created   extends OrderState
  case object Paid      extends OrderState
  case object Shipped   extends OrderState
  case object Delivered extends OrderState
  case object Cancelled extends OrderState

  // 订单事件
  sealed trait OrderEvent
  case object Pay     extends OrderEvent
  case object Ship    extends OrderEvent
  case object Deliver extends OrderEvent
  case object Cancel  extends OrderEvent

  // 核心：只定义合法的状态转换
  val orderTransition: PartialFunction[(OrderState, OrderEvent), OrderState] = {
    case (Created, Pay)     => Paid
    case (Created, Cancel)  => Cancelled
    case (Paid,    Ship)    => Shipped
    case (Paid,    Cancel)  => Cancelled
    case (Shipped, Deliver) => Delivered
  }

  // 对外暴露的状态转换 API，用 Either 表达成功/失败
  def transition(state: OrderState, event: OrderEvent): Either[String, OrderState] = {
    if (orderTransition.isDefinedAt((state, event)))
      Right(orderTransition((state, event)))
    else
      Left(s"非法操作: 订单状态 $state 不能执行 $event")
  }

  def main(args: Array[String]): Unit = {
    println("=== 场景一：电商订单状态机 ===\n")

    // 合法路径：Created -> Paid -> Shipped -> Delivered
    val testCases = List(
      (Created,   Pay),
      (Paid,      Ship),
      (Shipped,   Deliver),
      (Delivered, Cancel),  // 非法：已送达不能取消
      (Created,   Ship),    // 非法：未付款不能发货
      (Cancelled, Pay)      // 非法：已取消不能支付
    )

    testCases.foreach { case (state, event) =>
      transition(state, event) match {
        case Right(newState) => println(s"✓ $state + $event  =>  $newState")
        case Left(err)       => println(s"✗ $err")
      }
    }
  }
}
