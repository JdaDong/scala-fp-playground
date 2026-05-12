package demo.part02

/**
 * 柯里化(Currying)用法示例
 * 柯里化是将接受多个参数的函数转换为一系列接受单个参数的函数的过程
 */
object CurryingExamples {
  
  def main(args: Array[String]): Unit = {
    println("=== 柯里化用法示例 ===\n")
    
    // ========== 1. 基础柯里化 ==========
    println("1. 基础柯里化")
    println("-" * 20)
    
    // 定义柯里化函数
    def add(x: Int)(y: Int): Int = x + y
    def multiply(a: Int)(b: Int)(c: Int): Int = a * b * c
    
    // 使用柯里化函数
    println(s"add(3)(5) = ${add(3)(5)}")
    println(s"multiply(2)(3)(4) = ${multiply(2)(3)(4)}")
    
    // ========== 2. 部分应用函数 ==========
    println("\n2. 部分应用函数")
    println("-" * 20)
    
    val addFive: Int => Int = add(5)
    val multiplyByTwo: Int => Int => Int = multiply(2)
    val multiplyByTwoAndThree: Int => Int = multiplyByTwo(3)
    
    println(s"addFive(10) = ${addFive(10)}")  // 15
    println(s"multiplyByTwoAndThree(4) = ${multiplyByTwoAndThree(4)}")  // 24
    
    // ========== 3. 使用curried方法 ==========
    println("\n3. 使用curried方法")
    println("-" * 20)
    
    // 普通多参数函数
    def normalAdd(x: Int, y: Int): Int = x + y
    def normalMultiply(a: Int, b: Int, c: Int): Int = a * b * c
    
    // 转换为柯里化版本
    val curriedAdd = (normalAdd _).curried
    val curriedMultiply = (normalMultiply _).curried
    
    val addTen = curriedAdd(10)
    val multiplyByFive = curriedMultiply(5)
    val multiplyByFiveAndTwo = multiplyByFive(2)
    
    println(s"addTen(5) = ${addTen(5)}")  // 15
    println(s"multiplyByFiveAndTwo(3) = ${multiplyByFiveAndTwo(3)}")  // 30
    
    // ========== 4. 实际应用场景 ==========
    println("\n4. 实际应用场景")
    println("-" * 20)
    
    // 场景1: 配置化的函数
    def createLogger(level: String)(message: String)(timestamp: Long): Unit = 
      println(s"[$level][$timestamp] $message")
    
    val infoLogger: String => Long => Unit = createLogger("INFO")
    val errorLogger: String => Long => Unit = createLogger("ERROR")
    
    infoLogger("应用程序启动")(System.currentTimeMillis())
    errorLogger("数据库连接失败")(System.currentTimeMillis())
    
    // 场景2: 验证器工厂
    def createValidator(min: Int)(max: Int)(value: Int): Boolean = 
      value >= min && value <= max
    
    val ageValidator: Int => Int => Boolean = createValidator(18)
    val scoreValidator: Int => Int => Boolean = createValidator(0)
    
    println(s"年龄25是否有效: ${ageValidator(120)(25)}")
    println(s"分数85是否有效: ${scoreValidator(100)(85)}")
    
    // 场景3: 数学运算工厂
    def mathOperation(operation: String)(x: Double)(y: Double): Double = operation match {
      case "add" => x + y
      case "subtract" => x - y
      case "multiply" => x * y
      case "divide" => if (y != 0) x / y else Double.NaN
    }
    
    val addOperation: Double => Double => Double = mathOperation("add")
    val multiplyOperation: Double => Double => Double = mathOperation("multiply")
    
    println(s"加法: ${addOperation(5)(3)}")  // 8.0
    println(s"乘法: ${multiplyOperation(4)(2.5)}")  // 10.0
    
    // ========== 5. 高级柯里化技巧 ==========
    println("\n5. 高级柯里化技巧")
    println("-" * 20)
    
    // 自动柯里化
    def autoCurried[A, B, C](f: (A, B) => C): A => B => C = 
      a => b => f(a, b)
    
    def simpleAdd(x: Int, y: Int): Int = x + y
    val autoCurriedAdd = autoCurried(simpleAdd)
    println(s"自动柯里化: ${autoCurriedAdd(7)(8)}")  // 15
    
    // 反转柯里化参数
    def reverseCurry[A, B, C](f: A => B => C): (B, A) => C = 
      (b, a) => f(a)(b)
    
    val reversedAdd = reverseCurry(add)
    println(s"反转柯里化: ${reversedAdd(10, 5)}")  // 15
    
    // ========== 6. 柯里化与集合操作 ==========
    println("\n6. 柯里化与集合操作")
    println("-" * 20)
    
    val numbers = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    
    // 使用柯里化创建特定的过滤函数
    def createFilter(predicate: Int => Boolean)(list: List[Int]): List[Int] = 
      list.filter(predicate)
    
    val filterEven: List[Int] => List[Int] = createFilter(_ % 2 == 0)
    val filterGreaterThan5: List[Int] => List[Int] = createFilter(_ > 5)
    
    println(s"偶数: ${filterEven(numbers)}")
    println(s"大于5: ${filterGreaterThan5(numbers)}")
    
    // ========== 7. 类型安全的柯里化 ==========
    println("\n7. 类型安全的柯里化")
    println("-" * 20)
    
    // 使用隐式参数实现类型安全的柯里化
    def typedOperation[T](implicit numeric: Numeric[T]): T => T => T = 
      a => b => numeric.plus(a, b)
    
    val intAdd: Int => Int => Int = typedOperation[Int]
    val doubleAdd: Double => Double => Double = typedOperation[Double]
    
    println(s"Int加法: ${intAdd(3)(4)}")  // 7
    println(s"Double加法: ${doubleAdd(2.5)(3.5)}")  // 6.0
    
    println("\n=== 示例执行完成 ===")
  }
}

/**
 * 柯里化的优势总结:
 * 1. 代码复用: 通过部分应用创建专用函数
 * 2. 函数组合: 便于构建函数管道
 * 3. 类型安全: 编译时类型检查
 * 4. 配置管理: 将配置与逻辑分离
 * 5. 延迟执行: 可以推迟部分计算
 */