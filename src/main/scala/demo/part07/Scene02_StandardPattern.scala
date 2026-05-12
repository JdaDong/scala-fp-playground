package demo.part07

/**
 * ============================================================
 * Scene 02: Type Class 的标准三件套
 *
 *   一个"工业级"的 Type Class 通常包含：
 *     1) trait      —— 行为契约
 *     2) companion  —— 装"标准实例" + apply 方便取用
 *     3) syntax     —— 让你可以写 a.show 而不是 Show[A].show(a)
 *
 *   这就是 Cats / Scalaz 库里每个 Type Class 的"模板"。
 *   只要看懂这一节，Cats 源码你看哪个 type class 都不会迷路。
 *
 *   本场景从零实现一个 Show[A]（toString 的"安全版"）。
 * ============================================================
 */
object Scene02_StandardPattern {

  // ============================================================
  // ① Type Class：行为契约
  // ============================================================
  trait Show[A] {
    def show(a: A): String
  }

  // ============================================================
  // ② Companion object：放 apply + 标准实例
  // ============================================================
  object Show {

    // —— apply：方便取出 instance ——
    //   用法：Show[Int].show(42)  ← 比 implicitly[Show[Int]] 好看
    def apply[A](using sh: Show[A]): Show[A] = sh

    // —— 工厂方法：从函数构造一个 Show ——
    //   用法：Show.from[Person](_.name)
    def from[A](f: A => String): Show[A] = new Show[A] {
      def show(a: A): String = f(a)
    }

    // —— 标准 instance（Scala 3 的两种等价写法）——

    // 写法 a：given X: Show[T] with { def show ... }
    given intShow: Show[Int] with {
      def show(a: Int): String = s"Int($a)"
    }

    // 写法 b：given Show[T] = Show.from(...)
    given Show[String] = Show.from(s => s"\"$s\"")

    given Show[Boolean]                 = Show.from(b => if (b) "T" else "F")

    // 高阶 instance：依赖另一个 Show
    given listShow[A](using sh: Show[A]): Show[List[A]] with {
      def show(xs: List[A]): String =
        xs.map(sh.show).mkString("[", ", ", "]")
    }

    given optionShow[A](using sh: Show[A]): Show[Option[A]] with {
      def show(o: Option[A]): String = o match {
        case Some(a) => s"Some(${sh.show(a)})"
        case None    => "None"
      }
    }

    // 元组 Show（依赖 2 个 Show）
    given tupleShow[A, B](using sa: Show[A], sb: Show[B]): Show[(A, B)] with {
      def show(t: (A, B)): String = s"(${sa.show(t._1)}, ${sb.show(t._2)})"
    }
  }

  // ============================================================
  // ③ Syntax：扩展方法 → 让你写 a.show 而不是 Show[A].show(a)
  //
  //   Scala 3 用 extension 关键字
  //   Scala 2 要写 implicit class ShowOps[A](a: A) { ... }
  // ============================================================
  object syntax {
    extension [A](a: A)(using sh: Show[A]) {
      def show: String = sh.show(a)
    }
  }

  // ============================================================
  // 业务侧：定义自己的类，并给它一个 Show
  // ============================================================
  case class Person(name: String, age: Int)

  object Person {
    // 给 Person 注册 Show 实例（写在 companion 里就不需要 import）
    given Show[Person] = Show.from(p => s"Person(${p.name}, ${p.age})")
  }

  // ============================================================
  // 演示
  // ============================================================
  def main(args: Array[String]): Unit = {
    import syntax.show              // 导入扩展方法

    println("===== Scene02: Type Class 标准三件套 =====\n")

    // 用 apply 取实例
    println(s"  Show[Int].show(42) = ${Show[Int].show(42)}")

    // 用 syntax（扩展方法）—— 最优雅
    println(s"  42.show              = ${42.show}")
    println(s"  \"hi\".show            = ${"hi".show}")
    println(s"  true.show            = ${true.show}")

    // 嵌套类型（递归找 Show 实例）
    val xs: List[Option[Int]] = List(Some(1), None, Some(3))
    println(s"  List[Option[Int]]    = ${xs.show}")

    // 元组
    println(s"  (1, \"a\").show         = ${(1, "a").show}")

    // 自定义类
    val p = Person("Alice", 30)
    println(s"  Person.show          = ${p.show}")

    // List[Person] —— 完全没写代码，但自动有了 Show！
    val ps = List(Person("Alice", 30), Person("Bob", 25))
    println(s"  List[Person].show    = ${ps.show}")

    println("\n  ★ 关键观察：")
    println("    1. 业务类 Person 没继承任何东西，照样有 .show 能力")
    println("    2. List[Option[Int]]、List[Person] 这些组合类型的 Show，")
    println("       是编译器从 listShow + optionShow + intShow 自动拼出来的")
    println("    3. 这就叫 \"组合优于继承\" 的极致表达")

    println("\n===== Scene02 完成 =====")
  }
}
