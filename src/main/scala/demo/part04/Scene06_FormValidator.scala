package demo.part04

/**
 * 场景六：表单验证器（多规则组合）
 *
 * 【业务背景】
 *   Web 表单提交时需要多条校验规则：邮箱格式、年龄范围、手机号长度、密码强度…
 *   需求：
 *     - 每条规则独立定义、独立测试
 *     - 提交时收集"所有错误"，而不是遇到第一个就返回
 *     - 新增规则不影响旧代码
 *
 * 【偏函数的价值】
 *   - 每条规则用偏函数定义："违反规则时"返回错误信息，"通过时"未定义
 *   - lift 把 PartialFunction[Form, String] 变成 Form => Option[String]
 *   - flatMap 把所有规则应用到表单上，自动过滤掉 None（通过的规则）
 */
object Scene06_FormValidator {

  case class Form(email: String, age: Int, phone: String, password: String)

  // 类型别名：Validator 是"命中规则 -> 返回错误"的偏函数
  type Validator = PartialFunction[Form, String]

  // 规则 1：邮箱必须包含 @
  val emailValidator: Validator = {
    case f if !f.email.contains("@") => s"邮箱格式错误: ${f.email}"
  }

  // 规则 2：年龄 18-120
  val ageValidator: Validator = {
    case f if f.age < 18 || f.age > 120 => s"年龄必须在18-120之间，当前: ${f.age}"
  }

  // 规则 3：手机号必须 11 位数字
  val phoneValidator: Validator = {
    case f if !f.phone.matches("\\d{11}") => s"手机号必须是11位数字，当前: ${f.phone}"
  }

  // 规则 4：密码长度至少 8 位
  val passwordLengthValidator: Validator = {
    case f if f.password.length < 8 => s"密码长度至少8位，当前: ${f.password.length}"
  }

  // 规则 5：密码必须包含数字
  val passwordDigitValidator: Validator = {
    case f if !f.password.exists(_.isDigit) => "密码必须包含至少一个数字"
  }

  // 收集所有错误
  def validate(form: Form): List[String] = {
    val validators: List[Validator] = List(
      emailValidator,
      ageValidator,
      phoneValidator,
      passwordLengthValidator,
      passwordDigitValidator
    )
    // lift 把偏函数变成 Option：命中 -> Some(错误)，未命中 -> None
    validators.flatMap(_.lift(form))
  }

  def main(args: Array[String]): Unit = {
    println("=== 场景六：表单验证器 ===\n")

    val testForms = List(
      Form("invalid",       15,  "123",         "abc"),          // 全错
      Form("ok@ok.com",     25,  "13812345678", "password123"),  // 全对
      Form("bad",           200, "13812345678", "pwd"),          // 邮箱、年龄、密码错
      Form("good@mail.com", 30,  "13812345678", "abcdefgh")      // 密码无数字
    )

    testForms.zipWithIndex.foreach { case (form, idx) =>
      val errors = validate(form)
      println(s"表单 ${idx + 1}: $form")
      if (errors.isEmpty) {
        println("   ✓ 校验通过\n")
      } else {
        println(s"   ✗ 发现 ${errors.size} 个错误:")
        errors.foreach(e => println(s"      - $e"))
        println()
      }
    }
  }
}
