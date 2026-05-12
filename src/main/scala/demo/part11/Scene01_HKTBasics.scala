package demo.part11

/**
 * ============================================================
 * Scene 01: HKT —— 类型构造器的"参数化"
 *
 *   普通的"类型参数"：
 *     def head[A](xs: List[A]): A         ← A 是个具体类型
 *
 *   高阶的"类型构造器参数"（HKT）：
 *     def head[F[_], A](fa: F[A]): A      ← F 是一个"装东西的容器"，到底是 List 还是 Option，调用方决定
 *
 *   一句话：
 *     普通泛型让方法对"任意值类型"通用；
 *     HKT  让方法对"任意装值的容器"通用。
 *
 *   ★ 这是 cats / Scalaz 整个生态的"地基"。
 *   理解了 HKT，你才能看懂 Functor[F[_]] / Monad[F[_]] 这种签名。
 * ============================================================
 */
object Scene01_HKTBasics {

  // ============================================================
  // ① 没有 HKT 时的痛点：要给每个容器写一份
  // ============================================================
  object Without_HKT {
    def headListOrZero(xs: List[Int]): Int = xs.headOption.getOrElse(0)
    def headOptOrZero(o: Option[Int]): Int = o.getOrElse(0)
    def headVecOrZero(v: Vector[Int]): Int = v.headOption.getOrElse(0)
    // 还要 Set / Stream / 自定义容器... 写不完
  }

  // ============================================================
  // ② 用 HKT：一个签名通吃所有"能装东西"的类型
  // ============================================================
  // F[_] = "需要一个类型参数才能成为具体类型"
  //   List      ✅ 是 F[_]
  //   Option    ✅ 是 F[_]
  //   Either    ❌ 需要两个参数，不是 F[_]，但 Either[String, *] 是 F[_]
  //   Int       ❌ 已经是具体类型，不是 F[_]
  trait Container[F[_]] {
    def headOr[A](fa: F[A], default: A): A
    def map[A, B](fa: F[A])(f: A => B): F[B]
  }

  // ============================================================
  // ③ 给具体容器实现 Container instance
  // ============================================================
  given Container[List] with {
    def headOr[A](fa: List[A], default: A): A    = fa.headOption.getOrElse(default)
    def map[A, B](fa: List[A])(f: A => B): List[B] = fa.map(f)
  }

  given Container[Option] with {
    def headOr[A](fa: Option[A], default: A): A    = fa.getOrElse(default)
    def map[A, B](fa: Option[A])(f: A => B): Option[B] = fa.map(f)
  }

  given Container[Vector] with {
    def headOr[A](fa: Vector[A], default: A): A    = fa.headOption.getOrElse(default)
    def map[A, B](fa: Vector[A])(f: A => B): Vector[B] = fa.map(f)
  }

  // ============================================================
  // ④ ★ 写一个对"任意 F[_]"通用的函数
  // ============================================================
  def firstUpper[F[_]](fa: F[String])(using C: Container[F]): F[String] =
    C.map(fa)(_.toUpperCase)

  def headStrOrEmpty[F[_]](fa: F[String])(using C: Container[F]): String =
    C.headOr(fa, "")

  // ============================================================
  // ⑤ 部分应用：Either[E, *] 也能变成 F[_]
  //   语法：[a] =>> Either[String, a]   ← 类型 lambda
  // ============================================================
  type StrOr[A] = Either[String, A]

  given Container[StrOr] with {
    def headOr[A](fa: StrOr[A], default: A): A   = fa.getOrElse(default)
    def map[A, B](fa: StrOr[A])(f: A => B): StrOr[B] = fa.map(f)
  }

  // ============================================================
  // 演示
  // ============================================================
  def main(args: Array[String]): Unit = {
    println("===== Scene01: HKT 基础 =====\n")

    println("【1】同一个 firstUpper 函数，跑在不同容器上：")
    println(s"  List           : ${firstUpper(List("hello", "world"))}")
    println(s"  Option         : ${firstUpper(Option("hi"))}")
    println(s"  Vector         : ${firstUpper(Vector("a", "b", "c"))}")
    println(s"  Either[Str, *] : ${firstUpper(Right("scala"): StrOr[String])}")

    println("\n【2】headStrOrEmpty 同样多态：")
    println(s"  List           : ${headStrOrEmpty(List("first", "second"))}")
    println(s"  Option(None)   : ${headStrOrEmpty(Option.empty[String])}")
    println(s"  Either(Left)   : ${headStrOrEmpty(Left("oops"): StrOr[String])}")

    println(
      """
        |  ★ 关键观察：
        |    1. F[_] 让方法对"任意装值的容器"通用
        |    2. Container[F[_]] 是一个 Type Class，每种容器一个 given
        |    3. 这就是 cats Functor / Monad / Traverse 等的写法
        |    4. Either 这种 2 参数的，用 type lambda 部分应用变成 F[_]
        |""".stripMargin)
    println("===== Scene01 完成 =====")
  }
}
