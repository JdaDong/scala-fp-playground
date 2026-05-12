package demo.part07

import scala.deriving.Mirror
import scala.compiletime.{summonInline, erasedValue, constValue}

/**
 * ============================================================
 * Scene 03: Type Class 派生（Derivation）—— 实例可以"自动推导"
 *
 *   本场景用 3 个例子讲清"派生"这件事：
 *
 *     【例 1】手动派生：基于已有实例 → 推出新类型的实例
 *            （这其实就是 Scene02 里的 listShow / optionShow）
 *
 *     【例 2】辅助方法派生：用 contramap / imap 改造已有实例
 *            （类似 OOP 的"装饰器"，但完全是函数式的）
 *
 *     【例 3】case class 自动派生：用 Mirror 一行代码搞定
 *            （Scala 3 内建的元编程能力，无需宏库）
 *
 *   ★ 这是 Cats / circe / quill 等库"自动 JSON 编解码"的原理。
 * ============================================================
 */
object Scene03_Derivation {

  // ============================================================
  // 一个简化版的 Encoder type class
  //   ⚠️ 注意：这里用 trait（不是 object 内部嵌套），
  //   是为了让 `derives Encoder` 语法能正确找到 Encoder.derived
  // ============================================================
  trait Encoder[A] {
    def encode(a: A): String
  }

  object Encoder {
    def apply[A](using e: Encoder[A]): Encoder[A] = e
    def from[A](f: A => String): Encoder[A] = (a: A) => f(a)

    // —— 基础实例 ——
    given Encoder[Int]     = from(_.toString)
    given Encoder[Long]    = from(_.toString)
    given Encoder[String]  = from(s => s"\"$s\"")
    given Encoder[Boolean] = from(_.toString)

    // —— 派生：List / Option（写在这里，全局可见，避免 import）——
    given listEnc[A](using e: Encoder[A]): Encoder[List[A]] =
      from(xs => xs.map(e.encode).mkString("[", ",", "]"))

    given optEnc[A](using e: Encoder[A]): Encoder[Option[A]] =
      from {
        case Some(a) => e.encode(a)
        case None    => "null"
      }

    // ============================================================
    // 关键：`case class X derives Encoder` 编译器查找的是
    //       Encoder.derived[X]，因此必须把它放在 companion 里
    // ============================================================
    inline def summonEncoders[T <: Tuple]: List[Encoder[?]] =
      inline erasedValue[T] match {
        case _: EmptyTuple => Nil
        case _: (h *: t)   => summonInline[Encoder[h]] :: summonEncoders[t]
      }

    inline def labelsOf[T <: Tuple]: List[String] =
      inline erasedValue[T] match {
        case _: EmptyTuple => Nil
        case _: (h *: t)   => constValue[h].asInstanceOf[String] :: labelsOf[t]
      }

    inline def derived[A](using m: Mirror.ProductOf[A]): Encoder[A] = {
      val labels = labelsOf[m.MirroredElemLabels]
      val encs   = summonEncoders[m.MirroredElemTypes]
      from { (a: A) =>
        val product = a.asInstanceOf[Product]
        val parts   = labels.zip(encs).zipWithIndex.map {
          case ((label, e), i) =>
            val v = product.productElement(i)
            s"\"$label\":${e.asInstanceOf[Encoder[Any]].encode(v)}"
        }
        parts.mkString("{", ",", "}")
      }
    }
  }

  // ============================================================
  // 【例 1】手动派生：（实例已写在 Encoder companion 里）
  // ============================================================
  object Example1_Manual {
    def demo(): Unit = {
      println("【例 1】手动派生")
      println(s"  List[Int]            = ${Encoder[List[Int]].encode(List(1, 2, 3))}")
      println(s"  Option[String]       = ${Encoder[Option[String]].encode(Some("ok"))}")
      println(s"  List[Option[Int]]    = ${Encoder[List[Option[Int]]].encode(List(Some(1), None, Some(3)))}")
    }
  }

  // ============================================================
  // 【例 2】contramap：用 A→B 把 Encoder[B] 变成 Encoder[A]
  //
  //   一句话：如果你能把 A 转成已知如何编码的 B，
  //          那么 A 也"免费"获得了一个 Encoder
  //
  //   场景：业务有个 case class UserId(value: Long)，
  //        想让它直接像 Long 一样编码 → 写一行 contramap
  // ============================================================
  object Example2_Contramap {
    extension [B](eb: Encoder[B])
      def contramap[A](f: A => B): Encoder[A] = Encoder.from(a => eb.encode(f(a)))

    case class UserId(value: Long)
    case class Email(value: String)

    // 用 Long 的 Encoder + contramap 直接生成 UserId 的 Encoder
    given userIdEnc: Encoder[UserId] = Encoder[Long].contramap(_.value)
    given emailEnc:  Encoder[Email]  = Encoder[String].contramap(_.value)

    def demo(): Unit = {
      println("\n【例 2】contramap：从已有实例派生")
      println(s"  UserId(42)           = ${Encoder[UserId].encode(UserId(42L))}")
      println(s"  Email(\"a@b.com\")     = ${Encoder[Email].encode(Email("a@b.com"))}")
      println("  ★ 没有重复实现编码逻辑，只写了一行映射函数")
    }
  }

  // ============================================================
  // 【例 3】case class 自动派生（Scala 3 Mirror）
  //
  //   用户侧只需写：case class Person(...) derives Encoder
  //   编译器自动调用 Encoder.derived[Person] 生成实例
  // ============================================================
  // ★ 用户侧：只要在 case class 后加 `derives Encoder`
  case class Address(city: String, zip: Int) derives Encoder
  case class User(id: Long, name: String, active: Boolean, addr: Address) derives Encoder

  object Example3_AutoDerive {
    def demo(): Unit = {
      println("\n【例 3】case class 自动派生（Scala 3 Mirror）")
      val u = User(1L, "Alice", true, Address("Beijing", 100000))
      println(s"  User → ${Encoder[User].encode(u)}")
      println("  ★ Encoder[User] 完全没手写，编译器扫描字段自动拼出来！")
      println("    这就是 circe/jsoniter/快速 JSON 库的原理")
    }
  }

  // ============================================================
  // 入口
  // ============================================================
  def main(args: Array[String]): Unit = {
    println("===== Scene03: Type Class 派生（Derivation）=====\n")
    Example1_Manual.demo()
    Example2_Contramap.demo()
    Example3_AutoDerive.demo()
    println("\n===== Scene03 完成 =====")
  }
}
