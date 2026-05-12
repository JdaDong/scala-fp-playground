package demo.part03

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Success, Failure}

/**
 * Scala高级特性综合示例
 * 涵盖模式匹配、隐式转换、特质、类型系统、并发编程等
 */
object AdvancedScalaFeatures {
  
  def main(args: Array[String]): Unit = {
    println("=== Scala高级特性示例 ===\n")
    
    // ========== 1. 模式匹配 (Pattern Matching) ==========
    println("1. 模式匹配")
    println("-" * 20)
    
    // 基本模式匹配
    def describe(x: Any): String = x match {
      case 1 => "数字一"
      case "hello" => "问候语"
      case true => "布尔真"
      case Nil => "空列表"
      case _ => "其他"
    }
    
    println(s"describe(1) = ${describe(1)}")
    println(s"describe(\"hello\") = ${describe("hello")}")
    
    // 类型匹配和变量绑定
    def process(input: Any): String = input match {
      case s: String => s"字符串: $s"
      case i: Int if i > 0 => s"正整数: $i"
      case list: List[_] => s"列表长度: ${list.length}"
      case (a, b) => s"元组: ($a, $b)"
      case _ => "未知类型"
    }
    
    println(s"process(\"test\") = ${process("test")}")
    println(s"process(List(1,2,3)) = ${process(List(1,2,3))}")
    println(s"process((1, \"two\")) = ${process((1, "two"))}")
    
    // ========== 2. 隐式转换和隐式参数 ==========
    println("\n2. 隐式转换和隐式参数")
    println("-" * 20)
    
    // 隐式参数
    implicit val defaultMultiplier: Int = 2
    
    def multiplyWithImplicit(x: Int)(implicit factor: Int): Int = x * factor
    println(s"multiplyWithImplicit(5) = ${multiplyWithImplicit(5)}")
    
    // 隐式转换
    implicit class RichString(str: String) {
      def isPalindrome: Boolean = str == str.reverse
      def toTitleCase: String = str.split("\\s+").map(_.capitalize).mkString(" ")
    }
    
    val testString = "scala programming"
    println(s"'$testString' 是回文吗: ${testString.isPalindrome}")
    println(s"'$testString' 标题化: ${testString.toTitleCase}")
    
    // ========== 3. 特质 (Traits) 和多继承 ==========
    println("\n3. 特质和多继承")
    println("-" * 20)
    
    trait Logger {
      def log(message: String): Unit
      def info(message: String): Unit = log(s"[INFO] $message")
      def error(message: String): Unit = log(s"[ERROR] $message")
    }
    
    trait TimestampLogger extends Logger {
      abstract override def log(message: String): Unit = 
        super.log(s"${java.time.Instant.now()} $message")
    }
    
    class ConsoleLogger extends Logger {
      def log(message: String): Unit = println(message)
    }
    
    val logger = new ConsoleLogger with TimestampLogger
    logger.info("应用程序启动")
    logger.error("发生错误")
    
    // ========== 4. 类型系统高级特性 ==========
    println("\n4. 类型系统高级特性")
    println("-" * 20)
    
    // 泛型方法
    def findFirst[A](xs: List[A], p: A => Boolean): Option[A] = {
      xs.find(p)
    }
    
    val numbers = List(1, 2, 3, 4, 5)
    val names = List("Alice", "Bob", "Charlie")
    
    println(s"第一个偶数: ${findFirst(numbers, _ % 2 == 0)}")
    println(s"第一个B开头的名字: ${findFirst(names, _.startsWith("B"))}")
    
    // 类型边界
    class Container[A <: Comparable[A]](val value: A) {
      def compareTo(other: A): Int = value.compareTo(other)
    }
    
    val intContainer = new Container(10)
    println(s"比较结果: ${intContainer.compareTo(5)}")
    
    // ========== 5. 并发编程 (Future) ==========
    println("\n5. 并发编程")
    println("-" * 20)
    
    val future1 = Future {
      Thread.sleep(1000)
      "第一个异步任务完成"
    }
    
    val future2 = Future {
      Thread.sleep(500)
      "第二个异步任务完成"
    }
    
    val combinedFuture = for {
      result1 <- future1
      result2 <- future2
    } yield s"$result1, $result2"
    
    combinedFuture.onComplete {
      case Success(result) => println(s"异步结果: $result")
      case Failure(exception) => println(s"错误: ${exception.getMessage}")
    }
    
    // 等待异步任务完成
    Thread.sleep(1500)
    
    // ========== 6. 错误处理 (Try/Either) ==========
    println("\n6. 错误处理")
    println("-" * 20)
    
    def safeDivide(x: Int, y: Int): Try[Int] = Try(x / y)
    
    val result1 = safeDivide(10, 2)
    val result2 = safeDivide(10, 0)
    
    println(s"10 / 2 = ${result1.getOrElse("错误")}")
    println(s"10 / 0 = ${result2.getOrElse("除零错误")}")
    
    // Either 用于返回成功或错误信息
    def validateAge(age: Int): Either[String, Int] = 
      if (age >= 0 && age <= 150) Right(age) else Left("无效年龄")
    
    println(s"年龄25: ${validateAge(25)}")
    println(s"年龄-5: ${validateAge(-5)}")
    
    // ========== 7. 类型类 (Type Classes) ==========
    println("\n7. 类型类")
    println("-" * 20)
    
    trait Show[A] {
      def show(a: A): String
    }
    
    object Show {
      // 为Int提供Show实例
      implicit val intShow: Show[Int] = new Show[Int] {
        def show(a: Int): String = s"Int: $a"
      }
      
      // 为String提供Show实例
      implicit val stringShow: Show[String] = new Show[String] {
        def show(a: String): String = s"String: \"$a\""
      }
    }
    
    def printShowable[A](a: A)(implicit sh: Show[A]): Unit = 
      println(sh.show(a))
    
    printShowable(42)
    printShowable("Hello")
    
    // ========== 8. 宏和元编程 ==========
    println("\n8. 宏和元编程（概念性）")
    println("-" * 20)
    println("Scala支持编译时元编程，可以通过宏在编译时生成代码")
    println("例如：@inline, @tailrec, 自定义注解等")
    
    // 尾递归优化
    @annotation.tailrec
    def factorial(n: Int, acc: Int = 1): Int = {
      if (n <= 1) acc
      else factorial(n - 1, n * acc)
    }
    
    println(s"5的阶乘: ${factorial(5)}")
    
    println("\n=== 高级特性示例完成 ===")
  }
}

/**
 * Scala高级特性总结:
 * 1. 模式匹配 - 强大的条件分支和类型检查
 * 2. 隐式转换 - 自动类型转换和扩展方法
 * 3. 特质 - 类似接口但更强大的多继承机制
 * 4. 类型系统 - 泛型、类型边界、型变
 * 5. 并发编程 - Future、Actor模型
 * 6. 错误处理 - Try、Either、Option
 * 7. 类型类 - 隐式实现的接口模式
 * 8. 元编程 - 编译时代码生成和优化
 */