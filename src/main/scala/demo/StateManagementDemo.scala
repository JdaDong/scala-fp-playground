package demo

/**
 * 状态管理Demo - 订单状态机示例
 * 展示Scala模式匹配在状态机设计中的应用
 */
object StateManagementDemo {

  // 定义订单状态 sealed trait
  sealed trait OrderState
  case object Pending extends OrderState       // 待支付
  case object Paid extends OrderState          // 已支付
  case object Shipped extends OrderState       // 已发货
  case object Delivered extends OrderState     // 已送达
  case object Completed extends OrderState     // 已完成
  case object Cancelled extends OrderState     // 已取消
  case object Refunded extends OrderState      // 已退款

  // 定义订单事件
  sealed trait OrderEvent
  case object PaymentReceived extends OrderEvent     // 收到支付
  case object ShipOrder extends OrderEvent          // 发货
  case object DeliveryConfirmed extends OrderEvent  // 确认送达
  case object CompleteOrder extends OrderEvent      // 完成订单
  case object CancelOrder extends OrderEvent        // 取消订单
  case object RefundOrder extends OrderEvent        // 退款

  // 订单实体
  case class Order(id: String, state: OrderState, items: List[String], totalAmount: Double)

  /**
   * 状态转换函数
   * 使用模式匹配来处理状态转换逻辑
   */
  def transition(order: Order, event: OrderEvent): Either[String, Order] = {
    (order.state, event) match {
      // 从待支付状态转换
      case (Pending, PaymentReceived) =>
        Right(order.copy(state = Paid))
      case (Pending, CancelOrder) =>
        Right(order.copy(state = Cancelled))

      // 从已支付状态转换
      case (Paid, ShipOrder) =>
        Right(order.copy(state = Shipped))
      case (Paid, RefundOrder) =>
        Right(order.copy(state = Refunded))

      // 从已发货状态转换
      case (Shipped, DeliveryConfirmed) =>
        Right(order.copy(state = Delivered))

      // 从已送达状态转换
      case (Delivered, CompleteOrder) =>
        Right(order.copy(state = Completed))

      // 非法状态转换
      case (currentState, event) =>
        Left(s"无法从状态 $currentState 转换到事件 $event")
    }
  }

  /**
   * 状态验证器
   * 检查是否允许执行某个操作
   */
  def canPerformAction(order: Order, action: String): Boolean = order.state match {
    case Pending => action match {
      case "pay" | "cancel" => true
      case _ => false
    }
    case Paid => action match {
      case "ship" | "refund" => true
      case _ => false
    }
    case Shipped => action match {
      case "confirmDelivery" => true
      case _ => false
    }
    case Delivered => action match {
      case "complete" => true
      case _ => false
    }
    case _ => false
  }

  /**
   * 状态处理器 - 使用偏函数处理不同状态
   */
  val stateHandler: PartialFunction[(Order, OrderEvent), Either[String, Order]] = {
    case (order @ Order(_, Pending, _, _), PaymentReceived) =>
      println("处理支付...")
      Right(order.copy(state = Paid))
    
    case (order @ Order(_, Pending, _, _), CancelOrder) =>
      println("取消订单...")
      Right(order.copy(state = Cancelled))
    
    case (order @ Order(_, Paid, _, _), ShipOrder) =>
      println("发货处理...")
      Right(order.copy(state = Shipped))
  }

  /**
   * 演示状态管理
   */
  def demo(): Unit = {
    println("=== 订单状态管理Demo ===")
    
    // 创建初始订单
    val initialOrder = Order("ORD001", Pending, List("商品A", "商品B"), 199.99)
    println(s"初始订单状态: ${initialOrder.state}")
    
    // 状态转换演示
    val transitions = List(PaymentReceived, ShipOrder, DeliveryConfirmed, CompleteOrder)
    
    var currentOrder = initialOrder
    
    transitions.foreach { event =>
      println(s"\n尝试事件: $event")
      println(s"当前状态: ${currentOrder.state}")
      println(s"允许执行: ${canPerformAction(currentOrder, event.toString.toLowerCase)}")
      
      transition(currentOrder, event) match {
        case Right(newOrder) =>
          currentOrder = newOrder
          println(s"转换成功! 新状态: ${newOrder.state}")
        case Left(error) =>
          println(s"转换失败: $error")
      }
    }
    
    println(s"\n最终订单状态: ${currentOrder.state}")
    
    // 演示非法转换
    println("\n=== 演示非法状态转换 ===")
    val invalidTransition = transition(currentOrder, CancelOrder)
    println(invalidTransition.left.getOrElse("转换成功"))
  }

  /**
   * 高级状态机 - 带上下文的状态管理
   */
  case class StateMachine[S, E](currentState: S, history: List[(S, E)] = Nil) {
    def transition(transitionFn: (S, E) => Option[S], event: E): StateMachine[S, E] = {
      transitionFn(currentState, event) match {
        case Some(newState) =>
          StateMachine(newState, (currentState, event) :: history)
        case None =>
          this // 保持当前状态
      }
    }
    
    def canTransition(transitionFn: (S, E) => Option[S], event: E): Boolean = {
      transitionFn(currentState, event).isDefined
    }
    
    def getHistory: List[(S, E)] = history.reverse
  }

  // 使用高级状态机
  def advancedDemo(): Unit = {
    println("\n=== 高级状态机Demo ===")
    
    def orderTransition(state: OrderState, event: OrderEvent): Option[OrderState] = 
      (state, event) match {
        case (Pending, PaymentReceived) => Some(Paid)
        case (Pending, CancelOrder) => Some(Cancelled)
        case (Paid, ShipOrder) => Some(Shipped)
        case (Paid, RefundOrder) => Some(Refunded)
        case (Shipped, DeliveryConfirmed) => Some(Delivered)
        case (Delivered, CompleteOrder) => Some(Completed)
        case _ => None
      }
    
    val machine = StateMachine[OrderState, OrderEvent](Pending)
      .transition(orderTransition, PaymentReceived)
      .transition(orderTransition, ShipOrder)
      .transition(orderTransition, DeliveryConfirmed)
      .transition(orderTransition, CompleteOrder)
    
    println(s"最终状态: ${machine.currentState}")
    println(s"历史记录: ${machine.getHistory}")
  }

  def main(args: Array[String]): Unit = {
    demo()
    advancedDemo()
  }
}