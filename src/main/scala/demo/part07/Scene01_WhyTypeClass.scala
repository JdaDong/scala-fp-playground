package demo.part07

/**
 * ============================================================
 * Scene 01: 为什么需要 Type Class —— 从"OOP 接口"的痛点说起
 *
 *   场景：写一个 sum 函数，对集合求"和"
 *
 *   方案演进：
 *     A. 普通方法重载    → 写很多份，维护爆炸
 *     B. OOP 接口/抽象类 → 必须修改原类型，对 Int/String/第三方类无能为力
 *     C. 函数参数注入    → 调用方每次都要传，啰嗦
 *     D. ⭐ Type Class   → 既不改原类型、又自动注入、对所有类型统一
 *
 *   核心思想：
 *     "把行为从类型里剥离出来，放进一个独立的 trait（叫 type class），
 *      用 implicit / given 自动选择实现"
 *
 *   ★ 这是 Scala 区别于 Java 最深的特性：
 *     Java 只能 "类型 implements 接口" → 必须修改类型
 *     Scala 可以 "类型 + Type Class 实例" → 不用改类型也能赋予能力
 * ============================================================
 */
object Scene01_WhyTypeClass {

  // ============================================================
  // 方案 A：方法重载 —— 写很多份
  //   缺点：每多一种类型就多一个方法；不能写"通用 sum"
  // ============================================================
  object Approach_A_Overload {
    def sumInt(xs: List[Int]): Int          = xs.foldLeft(0)(_ + _)
    def sumLong(xs: List[Long]): Long       = xs.foldLeft(0L)(_ + _)
    def sumString(xs: List[String]): String = xs.foldLeft("")(_ + _)
    // 还要 sumDouble、sumBigDecimal、sumVector...
  }

  // ============================================================
  // 方案 B：OOP 接口 —— 必须修改原类型
  //   缺点 1：Int / String 是内置类型，你改不了
  //   缺点 2：第三方库的类，你也改不了
  // ============================================================
  object Approach_B_Interface {
    trait Addable[A] {
      def add(other: A): A
      def zero: A
    }

    // 你能给自己的类加，但 Int 加不了：
    case class Money(cents: Long) extends Addable[Money] {
      def add(other: Money): Money = Money(cents + other.cents)
      def zero: Money = Money(0L)
    }

    // ❌ 不能写：class Int extends Addable[Int] { ... }
    //   因为 Int 是 Scala 内置最终类
  }

  // ============================================================
  // 方案 C：函数参数注入 —— 把"行为"作为参数传进去
  //   优点：通用，对任何类型都能 sum
  //   缺点：每次调用都要手动传 zero 和 plus，啰嗦
  // ============================================================
  object Approach_C_Functions {
    def sum[A](xs: List[A], zero: A, plus: (A, A) => A): A =
      xs.foldLeft(zero)(plus)

    def demo(): Unit = {
      val s1 = sum(List(1, 2, 3), 0, (a: Int, b: Int) => a + b)
      val s2 = sum(List("a", "b"), "", (a: String, b: String) => a + b)
      println(s"    [C] $s1, $s2")
      // 调用方每次都要写 (a, b) => a + b，啰嗦
    }
  }

  // ============================================================
  // 方案 D ⭐：Type Class —— Scala 的最佳实践
  //
  //   3 步走：
  //     Step 1: 定义 Type Class trait（"行为契约"）
  //     Step 2: 为具体类型提供 instance（"行为实现"，用 given）
  //     Step 3: 写函数时，用 [A: Monoid] 或 (using Monoid[A]) 约束
  // ============================================================
  object Approach_D_TypeClass {

    // Step 1：定义 trait
    //   Monoid = 有"零元"和"结合二元运算"的类型
    //   （这是数学概念，但你只需要理解：能 zero、能 combine）
    trait Monoid[A] {
      def empty: A
      def combine(a: A, b: A): A
    }

    // Step 2：为各种类型提供 given 实例
    object Monoid {
      // Scala 3 语法：given 名字: 类型 = 值
      given intMonoid: Monoid[Int] with {
        def empty: Int = 0
        def combine(a: Int, b: Int): Int = a + b
      }

      given stringMonoid: Monoid[String] with {
        def empty: String = ""
        def combine(a: String, b: String): String = a + b
      }

      given listMonoid[A]: Monoid[List[A]] with {
        def empty: List[A] = Nil
        def combine(a: List[A], b: List[A]): List[A] = a ++ b
      }
    }

    // Step 3：用 using 约束（Scala 3）
    //   等价 Scala 2: def sum[A](xs: List[A])(implicit M: Monoid[A]): A
    def sum[A](xs: List[A])(using M: Monoid[A]): A =
      xs.foldLeft(M.empty)(M.combine)

    // 也可以用 context bound：[A: Monoid]
    //   编译器自动加 (using Monoid[A])
    def sum2[A: Monoid](xs: List[A]): A = {
      val M = summon[Monoid[A]]      // Scala 2 写 implicitly[Monoid[A]]
      xs.foldLeft(M.empty)(M.combine)
    }

    def demo(): Unit = {
      // 注意：调用时不用传任何"行为"参数，Scala 自动找 given！
      println(s"    [D] sum Int    = ${sum(List(1, 2, 3, 4, 5))}")
      println(s"    [D] sum String = ${sum(List("Hello", " ", "World"))}")
      println(s"    [D] sum List   = ${sum(List(List(1, 2), List(3, 4), List(5)))}")
    }
  }

  // ============================================================
  // 入口
  // ============================================================
  def main(args: Array[String]): Unit = {
    println("===== Scene01: Why Type Class =====\n")

    println("方案 A: 方法重载")
    println(s"    sumInt    = ${Approach_A_Overload.sumInt(List(1,2,3))}")
    println(s"    sumString = ${Approach_A_Overload.sumString(List("a","b","c"))}")

    println("\n方案 B: OOP 接口（只能给自己的类用）")
    val m = Approach_B_Interface.Money(100)
    println(s"    money.add = ${m.add(Approach_B_Interface.Money(50))}")
    println(s"    ❌ Int 没法 implement Addable")

    println("\n方案 C: 函数参数注入（啰嗦）")
    Approach_C_Functions.demo()

    println("\n方案 D ⭐: Type Class")
    Approach_D_TypeClass.demo()
    println("    ✓ 不修改原类型、不啰嗦、对所有类型统一")

    println("\n===== Scene01 完成 =====")
  }
}
