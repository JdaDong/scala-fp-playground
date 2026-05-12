package demo.part11

/**
 * ============================================================
 * Scene 02: Variance —— +A / -A / A 的精妙之处
 *
 *   Scala 的型变（variance）注解：
 *     class Box[+A]  —— 协变（covariant）   ：A 是"产出"位置
 *     class Box[-A]  —— 逆变（contravariant）：A 是"消费"位置
 *     class Box[A]   —— 不变（invariant）   ：A 既产又消
 *
 *   核心规则（"PECS"）：
 *     - 只读容器（如 List）→ 协变      ：List[Cat] 可以当 List[Animal] 用
 *     - 只写容器（如 Function input）→ 逆变  ：能处理 Animal 的，自然能处理 Cat
 *     - 读写都做（如 Array、可变 Set）→ 不变
 *
 *   函数 Function1[-A, +R] 是经典：参数逆变、返回值协变。
 *
 *   ★ 错误的 variance 标注会导致 type-safety 漏洞，
 *     正确理解它是写好库 API 的关键。
 * ============================================================
 */
object Scene02_Variance {

  // ============================================================
  // 业务领域：动物类层次
  // ============================================================
  class Animal { override def toString = "Animal" }
  class Cat extends Animal { override def toString = "Cat" }
  class Persian extends Cat { override def toString = "Persian" }

  // ============================================================
  // ① 协变 +A：只读容器，子类→父类自然成立
  // ============================================================
  object Covariance {
    class Box[+A](val value: A) {
      def get: A = value
    }
    // 注意：协变下，A 不能出现在"参数位置"（编译器拒绝），
    //   因为这会违反子类型安全。
    //   即不能写 def set(a: A): Unit  → 编译器报错

    def demo(): Unit = {
      println("【协变 +A】List 风格：只读")
      val cats: Box[Cat]        = Box(Cat())
      val animals: Box[Animal]  = cats   // ✅ 子类容器 → 父类容器
      println(s"  Box[Cat] 可赋给 Box[Animal] : ${animals.get}")
      println(s"  原因：取出来的东西本来就是 Cat，而 Cat is-a Animal")
    }
  }

  // ============================================================
  // ② 逆变 -A：只消费容器，父类→子类自然成立
  //   经典：Comparator / Function 的参数
  // ============================================================
  object Contravariance {
    trait Printer[-A] {
      def print(a: A): Unit
    }

    val animalPrinter: Printer[Animal] = (a: Animal) => println(s"    [animal printer] $a")
    // 一个能打印任何 Animal 的 Printer，自然也能用来打印 Cat：
    val catPrinter: Printer[Cat] = animalPrinter   // ✅ 父类 → 子类

    def demo(): Unit = {
      println("\n【逆变 -A】Printer / Comparator 风格：只消费")
      catPrinter.print(Cat())
      catPrinter.print(Persian())   // Persian 也是 Cat
      println(s"  Printer[Animal] 可赋给 Printer[Cat]")
      println(s"  原因：能处理父类，必然能处理子类")
    }
  }

  // ============================================================
  // ③ 不变 A：可读可写
  //   Array[A] 是不变的（JVM 真实数组协变是个老 bug）
  //   可变集合都是不变
  // ============================================================
  object Invariance {
    class MutBox[A](var value: A) {
      def get: A          = value
      def set(a: A): Unit = value = a
    }
    def demo(): Unit = {
      println("\n【不变 A】可变容器：必须不变")
      val cats: MutBox[Cat] = MutBox(Cat())
      // val animals: MutBox[Animal] = cats  // ❌ 编译失败 —— 否则可以塞进去 Dog 破坏 Cat
      println(s"  MutBox[Cat] 不能赋给 MutBox[Animal]（否则会被塞进非 Cat 元素）")
      println(s"  Scala 标准库的 Array、ListBuffer、Set 都是不变的")
    }
  }

  // ============================================================
  // ④ Function1[-A, +R] —— 经典案例
  //   规则：能处理父类输入的函数，自然能"假装"是处理子类的；
  //         能产生子类的函数，自然能"假装"是产生父类的。
  // ============================================================
  object FunctionVariance {
    // 一个"消费 Animal、产生 Persian"的函数
    val f: Animal => Persian = (_: Animal) => Persian()

    // 它可以被当作"消费 Cat、产生 Cat"的函数：
    //   - 输入端逆变：Cat 是 Animal 的子类 → 能消费 Animal 必能消费 Cat
    //   - 输出端协变：Persian 是 Cat 的子类 → 能产 Persian 必能产 Cat
    val g: Cat => Cat = f   // ✅

    def demo(): Unit = {
      println("\n【函数】Function1[-A, +R]")
      val r1 = f(Cat())
      val r2 = g(Persian())
      println(s"  f(Cat) = $r1")
      println(s"  g(Persian) = $r2")
      println(s"  Function1[Animal, Persian] 可赋给 Function1[Cat, Cat]")
    }
  }

  // ============================================================
  // ⑤ Variance 与 HKT 一起：F[+_]
  //   Option / List 在 cats / 标准库里都是 F[+_]，所以：
  //     Option[Cat] 是 Option[Animal] 的子类型
  //     List[Cat]   是 List[Animal] 的子类型
  // ============================================================
  object HKT_Variance {
    def demo(): Unit = {
      println("\n【HKT + Variance】")
      val cats: List[Cat]        = List(Cat(), Persian())
      val animals: List[Animal]  = cats   // ✅ List[+A]
      println(s"  List[Cat] → List[Animal]: $animals")

      val optC: Option[Cat]      = Some(Cat())
      val optA: Option[Animal]   = optC   // ✅ Option[+A]
      println(s"  Option[Cat] → Option[Animal]: $optA")
    }
  }

  // ============================================================
  // 入口
  // ============================================================
  def main(args: Array[String]): Unit = {
    println("===== Scene02: Variance =====\n")
    Covariance.demo()
    Contravariance.demo()
    Invariance.demo()
    FunctionVariance.demo()
    HKT_Variance.demo()
    println(
      """
        |  ★ 心法：
        |    "+A" → 子类容器可当父类容器（产出位置）
        |    "-A" → 父类容器可当子类容器（消费位置）
        |    " A" → 既产又消，无可代换关系
        |    Function1[-A, +R] → 输入逆变、输出协变（PECS 法则的语言级体现）
        |""".stripMargin)
    println("===== Scene02 完成 =====")
  }
}
