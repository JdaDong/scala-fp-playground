package demo.part11

/**
 * ============================================================
 * Scene 03: HKT 进阶 —— Kind 阶梯 + 自然变换 + Phantom Type
 *
 *   1) Kind（种类）阶梯：
 *      *           = 具体类型（Int, String, User）
 *      * → *       = 一参类型构造器（List, Option, Future）
 *      * → * → *   = 二参类型构造器（Either, Map, Function1）
 *      (* → *) → * = ★ 高阶类型构造器（如自由结构上的 Free）
 *
 *   2) 自然变换 F ~> G：从一个容器变到另一个容器
 *      （已经在 Tagless / Free 的 interpreter 里大量见过）
 *
 *   3) Phantom Type：类型参数不参与运行时，只用于编译期约束
 *      —— 真实业务里"类型安全 ID"、"未验证 vs 已验证"等场景的核心技术
 *
 *   ★ 这一节让你看到："类型系统不止是运行时校验，更是设计工具"
 * ============================================================
 */
object Scene03_HKTAdvanced {

  // ============================================================
  // 部分 1：自然变换 F ~> G
  //   forall A. F[A] => G[A]
  // ============================================================
  trait NaturalTransform[F[_], G[_]] {
    def apply[A](fa: F[A]): G[A]
  }
  // 取个简短的别名
  type ~>[F[_], G[_]] = NaturalTransform[F, G]

  // 例：把任何 List 转成 Option（取首元素）
  val listToOption: List ~> Option = new (List ~> Option) {
    def apply[A](fa: List[A]): Option[A] = fa.headOption
  }

  // 例：把任何 Option 当成 List（List 0/1 个）
  val optionToList: Option ~> List = new (Option ~> List) {
    def apply[A](fa: Option[A]): List[A] = fa.toList
  }

  def demo_NaturalTransform(): Unit = {
    println("===== 1. 自然变换 F ~> G =====")

    // 一个"对任何 F"操作的函数 + 一个 F~>G 转换 = 任意"切换容器"
    def lengthOfFirst[F[_]](fa: F[String], toOpt: F ~> Option): Int =
      toOpt(fa).map(_.length).getOrElse(0)

    println(s"  list   → ${lengthOfFirst(List("hello", "world"), listToOption)}")
    println(s"  option → ${lengthOfFirst(Some("hi"), new (Option ~> Option) { def apply[A](fa: Option[A]) = fa })}")

    println("  ★ 自然变换是 Tagless / Free 里 interpreter 的本质")
  }

  // ============================================================
  // 部分 2：Phantom Type —— "状态写到类型上"
  //
  //   场景：用户输入要先 validate 再 save
  //     传统：写注释 "请确保已校验"，但编译器不会帮你检查
  //     Phantom：在类型上区分 Raw / Validated，编译器强制
  // ============================================================
  object Phantom {
    sealed trait Raw          // phantom marker
    sealed trait Validated    // phantom marker

    // FormData[S] 中的 S 不在运行时存在，只是编译期标签
    case class FormData[S] private[Phantom] (email: String, age: Int) {
      override def toString = s"FormData($email, $age)"
    }
    object FormData {
      def fromInput(email: String, age: Int): FormData[Raw] =
        new FormData[Raw](email, age)
    }

    // 校验函数：消费 Raw 的，产出 Validated 的
    def validate(d: FormData[Raw]): Either[String, FormData[Validated]] =
      if (!d.email.contains("@"))      Left("非法 email")
      else if (d.age < 0 || d.age > 150) Left("非法 age")
      else                              Right(FormData[Validated](d.email, d.age))

    // ★ save 只接受已验证的 —— 类型签名强制保护
    def save(d: FormData[Validated]): Unit =
      println(s"  [save] 已验证数据落库：$d")

    def demo(): Unit = {
      println("\n===== 2. Phantom Type：让编译器替你看门 =====")
      val raw = FormData.fromInput("alice@x.com", 30)
      // save(raw)  ← ❌ 编译期就拒绝：FormData[Raw] 不能传给要 FormData[Validated] 的方法

      validate(raw) match {
        case Right(v) => save(v)             // ✅
        case Left(e)  => println(s"  [reject] $e")
      }

      val bad = FormData.fromInput("invalid", 30)
      validate(bad) match {
        case Right(v) => save(v)
        case Left(e)  => println(s"  [reject] $e")
      }

      println("  ★ Raw / Validated 在运行时不存在，零开销")
      println("    但编译器保证：save 永远不会拿到未验证的数据")
    }
  }

  // ============================================================
  // 部分 3：类型化 ID —— Phantom 的轻量级应用
  //   防止 UserId 当作 OrderId 误传（同样是 Long 但语义不同）
  // ============================================================
  object TypedIds {
    // 用 phantom tag 区分：value 都是 Long，但类型不同
    opaque type Id[Tag] = Long
    object Id {
      def apply[Tag](v: Long): Id[Tag] = v
    }
    // 把 extension 提到顶层，调用侧不需 import
    extension [Tag](id: Id[Tag]) def value: Long = id

    sealed trait UserTag
    sealed trait OrderTag

    type UserId  = Id[UserTag]
    type OrderId = Id[OrderTag]

    def loadUser(id: UserId): String   = s"User#${id.value}"
    def loadOrder(id: OrderId): String = s"Order#${id.value}"

    def demo(): Unit = {
      println("\n===== 3. 类型化 ID（opaque type + phantom tag）=====")
      val u: UserId  = Id[UserTag](42L)
      val o: OrderId = Id[OrderTag](100L)

      println(s"  loadUser(u)  → ${loadUser(u)}")
      println(s"  loadOrder(o) → ${loadOrder(o)}")
      // loadUser(o)  ← ❌ 编译失败：Id[OrderTag] 不是 Id[UserTag]
      println("  ★ UserId / OrderId 运行时都是 Long，零拆装箱开销")
      println("    但编译期：把 OrderId 传给 loadUser 直接报错")
    }
  }

  def main(args: Array[String]): Unit = {
    println("===== Scene03: HKT 进阶 =====\n")
    demo_NaturalTransform()
    Phantom.demo()
    TypedIds.demo()

    println(
      """
        |  ★ 心法总结：
        |    1. 自然变换 F ~> G —— interpreter 的本质，Tagless / Free 的灵魂
        |    2. Phantom Type     —— 让"状态/校验状态"写到类型里，编译器替你巡检
        |    3. opaque type + tag —— 类型安全 ID，零运行时开销
        |    4. 这些都是"用类型系统当设计工具"的真实手段
        |""".stripMargin)
    println("===== Scene03 完成 =====")
  }
}
