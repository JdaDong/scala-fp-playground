package demo.part05

/**
 * ============================================================
 * Scene 02: case class + 模式匹配 综合演练
 *
 * 核心知识点：
 *   1. case class 的"白送"功能：apply / unapply / equals / hashCode / copy / toString
 *   2. sealed trait + case class/object → 代数数据类型(ADT)，模式匹配编译期穷尽性检查
 *   3. 模式匹配的多种姿势：常量、类型、构造器、守卫、@绑定、嵌套、序列
 *   4. 实战应用：状态机 / AST 求值 / JSON 建模 / 表达式优化
 * ============================================================
 */
object Scene02_CaseClassPatternMatch {

  // ============================================================
  // 1. case class 的"白送"功能展示
  // ============================================================
  case class Point(x: Int, y: Int)

  def demo_CaseClassFeatures(): Unit = {
    println("===== 1. case class 自带能力 =====")

    val p1 = Point(1, 2)              // 不用 new
    val p2 = Point(1, 2)
    val p3 = p1.copy(y = 99)          // 不可变更新

    println(s"  p1 = $p1")            // 自动 toString
    println(s"  p1 == p2 ?  ${p1 == p2}")  // 自动 equals（true）
    println(s"  p3 = $p3")            // Point(1,99)

    // 解构
    val Point(a, b) = p1
    println(s"  解构: a=$a, b=$b")
    println()
  }

  // ============================================================
  // 2. ADT —— sealed trait + case class
  //    形状层次结构：编译器会检查 match 是否穷尽
  // ============================================================
  sealed trait Shape
  case class Circle(radius: Double)              extends Shape
  case class Rectangle(w: Double, h: Double)     extends Shape
  case class Triangle(a: Double, b: Double, c: Double) extends Shape
  case object EmptyShape                         extends Shape

  def area(s: Shape): Double = s match {
    case Circle(r)            => math.Pi * r * r
    case Rectangle(w, h)      => w * h
    case Triangle(a, b, c)    =>                          // Heron 公式
      val p = (a + b + c) / 2
      math.sqrt(p * (p - a) * (p - b) * (p - c))
    case EmptyShape           => 0.0
    // 如果漏写一个分支，编译器会警告："match may not be exhaustive"
  }

  def demo_ADT(): Unit = {
    println("===== 2. ADT + 穷尽性匹配 =====")
    val shapes: List[Shape] = List(
      Circle(3.0),
      Rectangle(4.0, 5.0),
      Triangle(3, 4, 5),
      EmptyShape
    )
    shapes.foreach { s =>
      println(f"  ${s.toString}%-30s 面积 = ${area(s)}%.2f")
    }
    println()
  }

  // ============================================================
  // 3. 模式匹配的全部姿势
  // ============================================================
  def describe(x: Any): String = x match {
    case 0                                  => "整数零"
    case n: Int if n < 0                    => s"负整数 $n"                 // 守卫
    case n: Int                             => s"正整数 $n"                 // 类型匹配
    case s: String if s.isEmpty             => "空字符串"
    case s: String                          => s"字符串 [$s]"
    case (a, b)                             => s"二元组 ($a, $b)"           // 元组解构
    case List()                             => "空列表"
    case List(only)                         => s"单元素列表 [$only]"        // 序列解构
    case head :: tail                       => s"头=$head 尾长度=${tail.size}"
    case List(1, 2, _*)                     => "以 1,2 开头的列表"
    case Some(v)                            => s"Option 有值 $v"
    case None                               => "Option 空"
    case arr: Array[_]                      => s"数组长度 ${arr.length}"
    case all @ Point(x, y) if x == y        => s"对角点 $all（@绑定整体）"   // @绑定
    case Point(x, y)                        => s"普通点 ($x,$y)"
    case _                                  => "未知类型"
  }

  def demo_AllPatterns(): Unit = {
    println("===== 3. 模式匹配全姿势 =====")
    val samples: List[Any] = List(
      0, -5, 42, "", "hello",
      (1, "a"), List.empty[Int], List(99), List(1, 2, 3, 4),
      Some("data"), None,
      Array(1, 2, 3),
      Point(7, 7), Point(1, 2),
      3.14
    )
    samples.foreach { s =>
      println(s"  describe($s) = ${describe(s)}")
    }
    println()
  }

  // ============================================================
  // 4. 实战 A：订单状态机
  // ============================================================
  sealed trait OrderState
  case object Created                                  extends OrderState
  case class  Paid(amount: Double)                     extends OrderState
  case class  Shipped(trackingNo: String)              extends OrderState
  case class  Delivered(timestamp: Long)               extends OrderState
  case class  Cancelled(reason: String)                extends OrderState

  sealed trait OrderEvent
  case class PayEvent(amount: Double)         extends OrderEvent
  case class ShipEvent(trackingNo: String)    extends OrderEvent
  case object DeliverEvent                    extends OrderEvent
  case class CancelEvent(reason: String)      extends OrderEvent

  /** 状态机的"灵魂"：用一个 match 表达所有的合法转换 */
  def transit(state: OrderState, event: OrderEvent): OrderState =
    (state, event) match {
      case (Created,    PayEvent(amt))      => Paid(amt)
      case (Paid(_),    ShipEvent(no))      => Shipped(no)
      case (Shipped(_), DeliverEvent)       => Delivered(System.currentTimeMillis)
      case (Created | Paid(_), CancelEvent(r)) => Cancelled(r)   // 联合模式
      case (s, e) =>
        println(s"  ⚠️  非法转换：$s + $e，状态保持")
        s
    }

  def demo_OrderStateMachine(): Unit = {
    println("===== 4. 实战A：订单状态机 =====")
    val events = List(
      PayEvent(99.9),
      ShipEvent("SF-12345"),
      DeliverEvent,
      CancelEvent("买错了")     // 已送达后再取消 → 非法
    )
    val finalState = events.foldLeft[OrderState](Created) { (st, ev) =>
      val next = transit(st, ev)
      println(s"  $st  --[$ev]-->  $next")
      next
    }
    println(s"  最终状态：$finalState")
    println()
  }

  // ============================================================
  // 5. 实战 B：表达式 AST 求值与化简
  // ============================================================
  sealed trait Expr
  case class Num(v: Double)                   extends Expr
  case class Var(name: String)                extends Expr
  case class Add(l: Expr, r: Expr)            extends Expr
  case class Mul(l: Expr, r: Expr)            extends Expr
  case class Neg(e: Expr)                     extends Expr

  /** 求值：用环境 env 把变量替换为具体的数 */
  def eval(e: Expr, env: Map[String, Double]): Double = e match {
    case Num(v)       => v
    case Var(n)       => env.getOrElse(n, throw new RuntimeException(s"未定义变量 $n"))
    case Add(l, r)    => eval(l, env) + eval(r, env)
    case Mul(l, r)    => eval(l, env) * eval(r, env)
    case Neg(x)       => -eval(x, env)
  }

  /** 化简：模式匹配做编译器优化（常量折叠 + 代数恒等式） */
  def simplify(e: Expr): Expr = e match {
    // 先递归化简子节点
    case Add(l, r) =>
      (simplify(l), simplify(r)) match {
        case (Num(0), x)        => x                            // 0 + x = x
        case (x, Num(0))        => x                            // x + 0 = x
        case (Num(a), Num(b))   => Num(a + b)                   // 常量折叠
        case (sl, sr)           => Add(sl, sr)
      }
    case Mul(l, r) =>
      (simplify(l), simplify(r)) match {
        case (Num(0), _) | (_, Num(0))  => Num(0)               // 0 * x = 0
        case (Num(1), x)                => x                    // 1 * x = x
        case (x, Num(1))                => x
        case (Num(a), Num(b))           => Num(a * b)
        case (sl, sr)                   => Mul(sl, sr)
      }
    case Neg(inner) => simplify(inner) match {
      case Num(v)     => Num(-v)
      case Neg(x)     => x                                       // --x = x
      case other      => Neg(other)
    }
    case leaf => leaf  // Num / Var
  }

  /** 漂亮打印 */
  def show(e: Expr): String = e match {
    case Num(v)       => if (v == v.toInt) v.toInt.toString else v.toString
    case Var(n)       => n
    case Add(l, r)    => s"(${show(l)} + ${show(r)})"
    case Mul(l, r)    => s"(${show(l)} * ${show(r)})"
    case Neg(x)       => s"(-${show(x)})"
  }

  def demo_ExprAST(): Unit = {
    println("===== 5. 实战B：表达式 AST =====")

    // (x + 0) * (1 + 2) + -(- y)   →  化简后为 x*3 + y
    val expr: Expr = Add(
      Mul(Add(Var("x"), Num(0)), Add(Num(1), Num(2))),
      Neg(Neg(Var("y")))
    )
    val simplified = simplify(expr)

    println(s"  原始: ${show(expr)}")
    println(s"  化简: ${show(simplified)}")

    val env = Map("x" -> 10.0, "y" -> 5.0)
    println(s"  在 x=10, y=5 时求值: ${eval(simplified, env)}")
    println()
  }

  // ============================================================
  // 6. 实战 C：JSON 模型 + 提取查询
  // ============================================================
  sealed trait Json
  case object JNull                            extends Json
  case class  JBool(v: Boolean)                extends Json
  case class  JNum(v: Double)                  extends Json
  case class  JStr(v: String)                  extends Json
  case class  JArr(items: List[Json])          extends Json
  case class  JObj(fields: Map[String, Json])  extends Json

  /** 从 JSON 中安全取出嵌套字段：xpath 类似 "user.address.city" */
  def query(json: Json, path: String): Option[Json] = {
    val keys = path.split("\\.").toList
    keys.foldLeft(Option(json)) { (acc, key) =>
      acc.flatMap {
        case JObj(fields) => fields.get(key)
        case _            => None
      }
    }
  }

  def demo_Json(): Unit = {
    println("===== 6. 实战C：JSON 模型 =====")
    val data: Json = JObj(Map(
      "user" -> JObj(Map(
        "name" -> JStr("Alice"),
        "age"  -> JNum(30),
        "address" -> JObj(Map(
          "city" -> JStr("北京"),
          "zip"  -> JStr("100000")
        ))
      )),
      "active" -> JBool(true),
      "tags"   -> JArr(List(JStr("vip"), JStr("new")))
    ))

    List("user.name", "user.address.city", "user.address.country", "active")
      .foreach { p =>
        val v = query(data, p) match {
          case Some(JStr(s))  => s"\"$s\""
          case Some(JNum(n))  => n.toString
          case Some(JBool(b)) => b.toString
          case Some(other)    => other.toString
          case None           => "<未找到>"
        }
        println(f"  query($p%-25s) = $v")
      }
    println()
  }

  // ============================================================
  // 7. 实战 D：访问者模式（OOP） vs 模式匹配（FP）
  //
  //   两种方式都能解决"对一个层次结构做多种操作"的问题。
  //   我们用同一个 Shape 层次，分别用两种风格各实现 area / perimeter。
  // ============================================================

  // ---- 7.1 OOP 访问者模式 ----
  // 每个 Shape 必须实现 accept；每个操作写成一个 Visitor。
  // 优点：加新操作只需新写一个 Visitor，旧代码不动。
  // 缺点：加新形状要改所有 Visitor；样板代码多；打开 -> 跳转很费劲。

  trait ShapeV { def accept[R](v: ShapeVisitor[R]): R }
  case class CircleV(r: Double)              extends ShapeV { def accept[R](v: ShapeVisitor[R]): R = v.visitCircle(this) }
  case class RectangleV(w: Double, h: Double) extends ShapeV { def accept[R](v: ShapeVisitor[R]): R = v.visitRectangle(this) }
  case class TriangleV(a: Double, b: Double, c: Double) extends ShapeV { def accept[R](v: ShapeVisitor[R]): R = v.visitTriangle(this) }

  trait ShapeVisitor[R] {
    def visitCircle(c: CircleV): R
    def visitRectangle(r: RectangleV): R
    def visitTriangle(t: TriangleV): R
  }

  object AreaVisitor extends ShapeVisitor[Double] {
    def visitCircle(c: CircleV): Double           = math.Pi * c.r * c.r
    def visitRectangle(r: RectangleV): Double     = r.w * r.h
    def visitTriangle(t: TriangleV): Double = {
      val p = (t.a + t.b + t.c) / 2
      math.sqrt(p * (p - t.a) * (p - t.b) * (p - t.c))
    }
  }

  object PerimeterVisitor extends ShapeVisitor[Double] {
    def visitCircle(c: CircleV): Double           = 2 * math.Pi * c.r
    def visitRectangle(r: RectangleV): Double     = 2 * (r.w + r.h)
    def visitTriangle(t: TriangleV): Double       = t.a + t.b + t.c
  }

  // ---- 7.2 Scala 模式匹配版本 ----
  // 直接复用文件前面定义的 Shape ADT（Circle / Rectangle / Triangle / EmptyShape）
  // 加新操作 = 多写一个函数；样板代码 = 0。
  def perimeter(s: Shape): Double = s match {
    case Circle(r)         => 2 * math.Pi * r
    case Rectangle(w, h)   => 2 * (w + h)
    case Triangle(a, b, c) => a + b + c
    case EmptyShape        => 0.0
  }

  def demo_VisitorVsMatch(): Unit = {
    println("===== 7. 实战D：访问者模式 vs 模式匹配 =====")

    // OOP 风格
    val shapesV: List[ShapeV] = List(CircleV(3), RectangleV(4, 5), TriangleV(3, 4, 5))
    println("  ▶ OOP 访问者模式：")
    shapesV.foreach { s =>
      val a = s.accept(AreaVisitor)
      val p = s.accept(PerimeterVisitor)
      println(f"    ${s.toString}%-30s area=$a%6.2f  perimeter=$p%6.2f")
    }

    // FP 模式匹配
    val shapesF: List[Shape] = List(Circle(3), Rectangle(4, 5), Triangle(3, 4, 5))
    println("  ▶ FP 模式匹配：")
    shapesF.foreach { s =>
      println(f"    ${s.toString}%-30s area=${area(s)}%6.2f  perimeter=${perimeter(s)}%6.2f")
    }

    println()
    println("  💡 表达式问题（Expression Problem）权衡：")
    println("     维度          | 访问者模式(OOP)         | 模式匹配(FP/ADT)")
    println("     --------------|-------------------------|-------------------------")
    println("     加新操作      | ✅ 新写一个 Visitor      | ✅ 新写一个函数")
    println("     加新数据类型  | ❌ 改所有 Visitor        | ⚠️  改所有 match")
    println("     代码样板量    | 多（accept/visit 双跳转）| 少（直接写 case）")
    println("     穷尽性检查    | ❌ 运行时才知道漏分支    | ✅ sealed 编译期警告")
    println("     可读性        | 低（控制流要跳两次）     | 高（同一处看到所有分支）")
    println()
    println("  📌 经验法则：")
    println("     - 数据类型【稳定】、操作【经常加】 → 模式匹配（绝大多数业务场景）")
    println("     - 数据类型【经常加】、操作【固定】 → 访问者模式（如编译器插件、IDE）")
    println()
  }

  // ============================================================
  // 入口
  // ============================================================
  def main(args: Array[String]): Unit = {
    demo_CaseClassFeatures()
    demo_ADT()
    demo_AllPatterns()
    demo_OrderStateMachine()
    demo_ExprAST()
    demo_Json()
    demo_VisitorVsMatch()
  }
}
