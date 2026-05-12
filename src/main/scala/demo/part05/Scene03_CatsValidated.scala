package demo.part05

import cats.data.{Validated, ValidatedNel, NonEmptyList}
import cats.syntax.validated.*       // .validNel / .invalidNel
import cats.syntax.apply.*           // (a, b, c).mapN(...)
import cats.syntax.either.*          // .toValidatedNel

/**
 * ============================================================
 * Scene 03: Cats 真实库的 Validated 用法
 *
 *   对比对象：Scene01_ForComprehension 中我们【手写】的简化版
 *
 *   Cats Validated 的核心类型：
 *     Validated[+E, +A]            ：Valid(a) 或 Invalid(e)
 *     ValidatedNel[+E, +A]         ：Validated[NonEmptyList[E], A]    ← 推荐
 *     ValidatedNec[+E, +A]         ：Validated[NonEmptyChain[E], A]   ← 拼接更快
 *
 *   核心 API：
 *     "abc".validNel[String]       ：Valid("abc")
 *     "err".invalidNel[String]     ：Invalid(NEL.of("err"))
 *     (va, vb, vc).mapN(f)         ：Applicative 组合，错误自动累积
 *     either.toValidatedNel        ：Either[E,A] → ValidatedNel[E,A]
 *
 *   何时用 Cats 而不是手写？
 *     ✅ 错误类型可以是任意 Semigroup（不只是 List[String]）
 *     ✅ mapN 直接支持任意元数 (2~22)，不用自己写 map4/map5
 *     ✅ 能和 cats-effect / circe / http4s 等生态无缝互操作
 *     ✅ 工具函数齐全：traverse / sequence / fold / leftMap / andThen
 * ============================================================
 */
object Scene03_CatsValidated {

  // ============================================================
  // 1. 表单数据 + 校验函数（返回 ValidatedNel[String, A]）
  // ============================================================
  case class RegisterForm(name: String, email: String, age: Int, password: String)

  // 写法一：直接用 .validNel / .invalidNel 语法糖
  def validateName(name: String): ValidatedNel[String, String] =
    if (name.trim.nonEmpty) name.trim.validNel
    else "姓名不能为空".invalidNel

  // 写法二：从 Either 转换（适合复用已有的 Either 校验逻辑）
  def validateEmail(email: String): ValidatedNel[String, String] = {
    val e: Either[String, String] =
      if (email.contains("@") && email.contains(".")) Right(email)
      else Left(s"邮箱格式错误：$email")
    e.toValidatedNel
  }

  def validateAge(age: Int): ValidatedNel[String, Int] =
    if (age >= 18 && age <= 120) age.validNel
    else s"年龄非法：$age（必须 18-120）".invalidNel

  def validatePassword(pwd: String): ValidatedNel[String, String] =
    if (pwd.length >= 8) pwd.validNel
    else "密码至少 8 位".invalidNel

  // ============================================================
  // 2. 用 mapN 组合 —— 这是 Cats Validated 的灵魂
  //    重点：编译期支持 2~22 元，错误自动通过 NEL 拼接
  // ============================================================
  def register(form: RegisterForm): ValidatedNel[String, RegisterForm] =
    (
      validateName(form.name),
      validateEmail(form.email),
      validateAge(form.age),
      validatePassword(form.password)
    ).mapN(RegisterForm.apply)

  def demo_BasicMapN(): Unit = {
    println("===== 1. Cats Validated.mapN 错误累积 =====")

    val cases = List(
      ("✅ 全部正确",   RegisterForm("Alice", "alice@gmail.com", 25, "12345678")),
      ("❌ 1 个错误",   RegisterForm("Bob",   "bad-email",       30, "12345678")),
      ("❌ 4 个错误",   RegisterForm("",      "bad-email",       15, "123")),
    )

    cases.foreach { case (label, form) =>
      println(s"  [$label]")
      register(form) match {
        case Validated.Valid(ok) =>
          println(s"    ✅ 注册成功：${ok.name}")
        case Validated.Invalid(errs: NonEmptyList[String]) =>
          println(s"    ❌ 共 ${errs.size} 个错误：")
          errs.toList.foreach(e => println(s"       - $e"))
      }
    }
    println()
  }

  // ============================================================
  // 3. 强类型错误 —— 不只是 String
  //    真实业务中，错误最好是 sealed trait / case class，
  //    方便国际化、序列化、统计。
  // ============================================================
  sealed trait FormError {
    def code: String
    def message: String
  }
  object FormError {
    case object EmptyName            extends FormError { val code = "E001"; val message = "姓名不能为空"   }
    case class  BadEmail(raw: String) extends FormError { val code = "E002"; val message = s"邮箱格式错误：$raw" }
    case class  BadAge(value: Int)    extends FormError { val code = "E003"; val message = s"年龄非法：$value" }
    case object PasswordTooShort     extends FormError { val code = "E004"; val message = "密码至少 8 位"   }
  }

  import FormError.*

  def vName(n: String): ValidatedNel[FormError, String] =
    if (n.trim.nonEmpty) n.trim.validNel else EmptyName.invalidNel

  def vEmail(e: String): ValidatedNel[FormError, String] =
    if (e.contains("@") && e.contains(".")) e.validNel else BadEmail(e).invalidNel

  def vAge(a: Int): ValidatedNel[FormError, Int] =
    if (a >= 18 && a <= 120) a.validNel else BadAge(a).invalidNel

  def vPwd(p: String): ValidatedNel[FormError, String] =
    if (p.length >= 8) p.validNel else PasswordTooShort.invalidNel

  def registerTyped(form: RegisterForm): ValidatedNel[FormError, RegisterForm] =
    (vName(form.name), vEmail(form.email), vAge(form.age), vPwd(form.password))
      .mapN(RegisterForm.apply)

  def demo_TypedError(): Unit = {
    println("===== 2. 强类型错误 (sealed trait) =====")
    val bad = RegisterForm("", "bad", 15, "123")
    registerTyped(bad) match {
      case Validated.Valid(_) => println("    不会到这里")
      case Validated.Invalid(errs) =>
        println(s"    ❌ 共 ${errs.size} 个错误：")
        errs.toList.foreach { e =>
          println(f"       [${e.code}%s] ${e.message}")
        }
    }
    println()
  }

  // ============================================================
  // 4. 批量校验 —— traverse 的威力
  //    需求：一次性注册 N 个用户，要么全部成功，要么把所有用户、所有错误一次返回。
  // ============================================================
  import cats.syntax.traverse.*

  def registerBatch(forms: List[RegisterForm])
    : ValidatedNel[FormError, List[RegisterForm]] =
    forms.traverse(registerTyped)
    //   List[RegisterForm] => ValidatedNel[FormError, List[RegisterForm]]
    //
    //   traverse 会：
    //   1) 对每个元素调用 registerTyped
    //   2) 如果全部 Valid，结果是 Valid(List(...))
    //   3) 任何一个 Invalid 都会把错误"累积"到 NEL 里

  def demo_BatchTraverse(): Unit = {
    println("===== 3. 批量校验：traverse =====")
    val forms = List(
      RegisterForm("Alice",   "alice@x.com",  25, "12345678"),  // ✅
      RegisterForm("",        "bob@x.com",    30, "12345678"),  // ❌ 名字
      RegisterForm("Carol",   "no-at-sign",   28, "12345678"),  // ❌ 邮箱
      RegisterForm("David",   "d@x.com",      10, "short"),     // ❌ 年龄+密码
    )

    registerBatch(forms) match {
      case Validated.Valid(list) =>
        println(s"    全部成功：${list.size} 人")
      case Validated.Invalid(errs) =>
        println(s"    ❌ 批量校验失败，共 ${errs.size} 个错误：")
        errs.toList.foreach(e => println(s"       [${e.code}] ${e.message}"))
    }
    println()
  }

  // ============================================================
  // 5. Either 与 Validated 的相互转换 + andThen 串联依赖步骤
  //
  //   Validated 没有 flatMap（不支持 for），但有 andThen，
  //   用于"前一步成功后才能做下一步"的场景：
  //     v1.andThen(a => f(a))   类似 flatMap，但必须显式
  // ============================================================
  def demo_AndThen(): Unit = {
    println("===== 4. andThen：累积 + 依赖 的混合 =====")

    // 模拟：先校验所有字段（累积错误），全部通过后再去数据库查重（依赖步骤）
    def checkUnique(name: String): ValidatedNel[FormError, String] =
      if (name == "Alice") EmptyName.invalidNel    // 假设 Alice 重复了
      else name.validNel

    val form = RegisterForm("Alice", "a@x.com", 25, "12345678")

    val result = registerTyped(form).andThen { ok =>
      // 只有所有字段都校验通过，才会进到这里
      checkUnique(ok.name).map(_ => ok)
    }

    result match {
      case Validated.Valid(ok)     => println(s"    ✅ 注册成功：$ok")
      case Validated.Invalid(errs) =>
        println(s"    ❌ 失败：")
        errs.toList.foreach(e => println(s"       [${e.code}] ${e.message}"))
    }
    println()
  }

  // ============================================================
  // 6. 与手写版的对比小结
  // ============================================================
  def demo_ComparisonSummary(): Unit = {
    println("===== 5. 手写 Validated vs Cats Validated =====")
    val table =
      """|  维度          | 手写版（Scene01）          | Cats Validated
         |  --------------|----------------------------|------------------------------------
         |  错误类型      | List[String] 写死          | 任意 Semigroup（NEL/NEC/Set/Map...）
         |  组合 API      | 自己写 map2/map3/map4      | mapN 通用（2~22 元）
         |  依赖步骤      | 不支持                     | andThen
         |  批量校验      | 自己 fold                  | traverse / sequence
         |  生态互通      | 无                         | cats-effect / circe / http4s ...
         |  学习成本      | 低                         | 中（需要懂 Applicative）
         |  适用阶段      | 入门理解原理               | 生产项目""".stripMargin
    println(table)
    println()
  }

  // ============================================================
  // 入口
  // ============================================================
  def main(args: Array[String]): Unit = {
    demo_BasicMapN()
    demo_TypedError()
    demo_BatchTraverse()
    demo_AndThen()
    demo_ComparisonSummary()
  }
}
