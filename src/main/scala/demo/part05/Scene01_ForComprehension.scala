package demo.part05

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * ============================================================
 * Scene 01: for 推导式实战
 *   场景一：Option  —— 链式取值，告别空指针
 *   场景二：Future  —— 异步组合，告别回调地狱
 *   场景三：Either  —— 业务流水线，错误信息不丢失
 *   场景四：Try     —— 异常安全的链式调用
 *
 * 核心口诀：
 *   for { x <- A; y <- B; z <- C } yield f(x,y,z)
 *   等价于：A.flatMap(x => B.flatMap(y => C.map(z => f(x,y,z))))
 *
 * for 推导式的"短路"特性：
 *   只要中间有一步是 None / Failure / Left，后续就不会执行
 * ============================================================
 */
object Scene01_ForComprehension {

  // ============================================================
  // 场景一：Option —— 多层嵌套取值
  // ============================================================

  case class Address(city: Option[String], zip: Option[String])
  case class Profile(address: Option[Address])
  case class User(name: String, profile: Option[Profile])

  // 模拟数据库
  val userDb: Map[Int, User] = Map(
    1 -> User("Alice", Some(Profile(Some(Address(Some("北京"), Some("100000")))))),
    2 -> User("Bob",   Some(Profile(Some(Address(None,       Some("200000")))))),
    3 -> User("Carol", Some(Profile(None))),
    4 -> User("David", None)
  )

  /** 不用 for —— Java 风格的嵌套 if，丑且容易漏 */
  def getCity_Ugly(id: Int): String = {
    val userOpt = userDb.get(id)
    if (userOpt.isDefined) {
      val user = userOpt.get
      if (user.profile.isDefined) {
        val profile = user.profile.get
        if (profile.address.isDefined) {
          val addr = profile.address.get
          if (addr.city.isDefined) addr.city.get
          else "未知"
        } else "未知"
      } else "未知"
    } else "未知"
  }

  /** 用 for 推导式 —— 优雅、可读、安全 */
  def getCity_Elegant(id: Int): String = {
    val cityOpt: Option[String] = for {
      user    <- userDb.get(id)        // Option[User]
      profile <- user.profile           // Option[Profile]
      addr    <- profile.address        // Option[Address]
      city    <- addr.city              // Option[String]
    } yield city                        // Option[String]

    cityOpt.getOrElse("未知")
  }

  def runOptionDemo(): Unit = {
    println("===== 场景一：Option 链式取值 =====")
    (1 to 5).foreach { id =>
      println(s"  用户 $id 的城市：${getCity_Elegant(id)}")
    }
    println()
  }

  // ============================================================
  // 场景二：Future —— 异步任务编排
  // ============================================================

  implicit val ec: ExecutionContext = ExecutionContext.global

  case class UserInfo(id: Int, name: String)
  case class Order(orderId: String, userId: Int, amount: Double)
  case class Discount(rate: Double)

  def fetchUser(id: Int): Future[UserInfo] = Future {
    Thread.sleep(100)
    println(s"  [异步] 拉取用户信息 id=$id")
    UserInfo(id, s"User-$id")
  }

  def fetchOrders(userId: Int): Future[List[Order]] = Future {
    Thread.sleep(150)
    println(s"  [异步] 拉取订单 userId=$userId")
    List(
      Order("O-001", userId, 100.0),
      Order("O-002", userId, 200.0),
      Order("O-003", userId, 50.0)
    )
  }

  def fetchDiscount(userId: Int): Future[Discount] = Future {
    Thread.sleep(80)
    println(s"  [异步] 拉取折扣 userId=$userId")
    Discount(0.9)
  }

  /** 串行依赖 + 并行优化的混合用法 */
  def calculateUserBill(userId: Int): Future[String] = {
    // 关键技巧：并行任务在 for 之外启动，for 里只组合结果
    val ordersF   = fetchOrders(userId)
    val discountF = fetchDiscount(userId)

    for {
      user     <- fetchUser(userId)        // 第一步：必须先拿到用户
      orders   <- ordersF                  // 这里 orders 和 discount 并行进行
      discount <- discountF
    } yield {
      val total = orders.map(_.amount).sum * discount.rate
      f"用户 ${user.name} 共 ${orders.size} 笔订单，应付：$total%.2f"
    }
  }

  /** 失败处理：recover */
  def safeBill(userId: Int): Future[String] =
    calculateUserBill(userId).recover {
      case e: Throwable => s"账单计算失败：${e.getMessage}"
    }

  def runFutureDemo(): Unit = {
    println("===== 场景二：Future 异步编排 =====")
    val result = Await.result(safeBill(1001), 5.seconds)
    println(s"  结果：$result")
    println()
  }

  // ============================================================
  // 场景三：Either —— 业务流水线（带错误原因）
  //   约定：Left = 错误信息，Right = 成功值
  // ============================================================

  case class RegisterForm(name: String, email: String, age: Int, password: String)

  def validateName(name: String): Either[String, String] =
    if (name.trim.nonEmpty) Right(name.trim)
    else Left("姓名不能为空")

  def validateEmail(email: String): Either[String, String] =
    if (email.contains("@") && email.contains(".")) Right(email)
    else Left(s"邮箱格式错误：$email")

  def validateAge(age: Int): Either[String, Int] =
    if (age >= 18 && age <= 120) Right(age)
    else Left(s"年龄非法：$age（必须在 18-120 之间）")

  def validatePassword(pwd: String): Either[String, String] =
    if (pwd.length >= 8) Right(pwd)
    else Left("密码至少 8 位")

  /** 用 for 串联所有校验，任何一步失败立即返回 Left */
  def register(form: RegisterForm): Either[String, RegisterForm] =
    for {
      n <- validateName(form.name)
      e <- validateEmail(form.email)
      a <- validateAge(form.age)
      p <- validatePassword(form.password)
    } yield RegisterForm(n, e, a, p)

  def runEitherDemo(): Unit = {
    println("===== 场景三：Either 业务校验流水线 =====")
    val cases = List(
      RegisterForm("Alice", "alice@gmail.com", 25, "12345678"),    // ok
      RegisterForm("",      "alice@gmail.com", 25, "12345678"),    // 名字空
      RegisterForm("Bob",   "bob#gmail.com",   30, "12345678"),    // 邮箱错
      RegisterForm("Carol", "c@x.com",         15, "12345678"),    // 年龄小
      RegisterForm("David", "d@x.com",         40, "123")          // 密码短
    )

    cases.foreach { f =>
      register(f) match {
        case Right(ok) => println(s"  ✅ 注册成功：${ok.name}")
        case Left(err) => println(s"  ❌ 失败：$err")
      }
    }
    println()
  }

  // ============================================================
  // 场景四：Try —— 异常安全的链式
  // ============================================================

  def parseInt(s: String): Try[Int]      = Try(s.trim.toInt)
  def divide(a: Int, b: Int): Try[Int]   = Try(a / b)
  def sqrt(x: Int): Try[Double]          =
    if (x < 0) Failure(new IllegalArgumentException(s"负数无法开方: $x"))
    else Success(math.sqrt(x.toDouble))

  /** 计算 sqrt(parseInt(a) / parseInt(b)) */
  def compute(a: String, b: String): Try[Double] =
    for {
      x <- parseInt(a)
      y <- parseInt(b)
      q <- divide(x, y)
      r <- sqrt(q)
    } yield r

  def runTryDemo(): Unit = {
    println("===== 场景四：Try 异常安全链 =====")
    val cases = List(("100", "4"), ("abc", "4"), ("100", "0"), ("-100", "4"), ("16", "1"))
    cases.foreach { case (a, b) =>
      compute(a, b) match {
        case Success(v) => println(f"  compute($a, $b) = $v%.4f")
        case Failure(e) => println(s"  compute($a, $b) 失败：${e.getMessage}")
      }
    }
    println()
  }

  // ============================================================
  // 场景五：Validated —— 错误"累积"  vs  Either 的"短路"
  //
  //   痛点：用 Either 做表单校验，第一个错误就 return，
  //         用户改完一个发现还有第二个，体验极差。
  //
  //   方案：自定义一个"轻量 Validated"，把所有错误用 List 累积。
  //         真实项目里直接用 cats.data.Validated 即可。
  //
  //   关键差异：
  //     - Either   是 Monad（flatMap）→ for 推导式 → 短路
  //     - Validated 是 Applicative（mapN）→ 并行组合 → 累积
  // ============================================================

  /** 自定义 Validated：成功带值，失败带错误列表（NEL 简化版） */
  sealed trait Validated[+A] {
    def isValid: Boolean = this.isInstanceOf[Valid[_]]
  }
  case class Valid[A](value: A)               extends Validated[A]
  case class Invalid(errors: List[String])    extends Validated[Nothing]

  object Validated {
    def fromEither[A](e: Either[String, A]): Validated[A] = e match {
      case Right(v)  => Valid(v)
      case Left(err) => Invalid(List(err))
    }

    /** Applicative 风格的 4 元组合：错误会被合并 */
    def map4[A, B, C, D, R](
      va: Validated[A], vb: Validated[B], vc: Validated[C], vd: Validated[D]
    )(f: (A, B, C, D) => R): Validated[R] = (va, vb, vc, vd) match {
      case (Valid(a), Valid(b), Valid(c), Valid(d)) => Valid(f(a, b, c, d))
      case _ =>
        // 把所有 Invalid 的错误合并到一起（这就是"累积"）
        val errs = List(va, vb, vc, vd).collect { case Invalid(es) => es }.flatten
        Invalid(errs)
    }
  }

  /** 用 Validated 重做注册校验：复用之前的 Either 校验函数 */
  def registerAccumulate(form: RegisterForm): Validated[RegisterForm] = {
    import Validated._
    map4(
      fromEither(validateName(form.name)),
      fromEither(validateEmail(form.email)),
      fromEither(validateAge(form.age)),
      fromEither(validatePassword(form.password))
    )((n, e, a, p) => RegisterForm(n, e, a, p))
  }

  def runValidatedDemo(): Unit = {
    println("===== 场景五：Validated 错误累积 vs Either 短路 =====")

    // 一条记录里包含 3 个错误：名字空、邮箱错、密码短
    val bad = RegisterForm("", "bad-email", 15, "123")

    println("  ▶ Either（短路，只能看到第一个错误）:")
    register(bad) match {
      case Right(ok) => println(s"    ok: $ok")
      case Left(err) => println(s"    ❌ $err")
    }

    println("  ▶ Validated（累积，所有错误一次给出）:")
    registerAccumulate(bad) match {
      case Valid(ok)       => println(s"    ok: $ok")
      case Invalid(errors) =>
        println(s"    ❌ 共 ${errors.size} 个错误：")
        errors.foreach(e => println(s"       - $e"))
    }
    println()

    println("  💡 何时用谁？")
    println("     - 步骤之间有【数据依赖】（B 依赖 A 的结果）→ 用 Either / for 推导式")
    println("     - 步骤之间【相互独立】（表单字段、配置项校验）→ 用 Validated 累积错误")
    println()
  }

  // ============================================================
  // 入口
  // ============================================================
  def main(args: Array[String]): Unit = {
    runOptionDemo()
    runFutureDemo()
    runEitherDemo()
    runTryDemo()
    runValidatedDemo()
  }
}
