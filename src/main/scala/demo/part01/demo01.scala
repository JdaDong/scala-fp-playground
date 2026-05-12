package demo.part01;

class Animal(val name: String) {
    val weight: Int = 10
}

class Cat(name: String) extends Animal(name) {
    override val weight: Int = 11
}

object demo01 {
    def main(args: Array[String]): Unit = {
        val numbers = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        numbers.foreach(println)
        val sum = numbers.reduce(_ + _)
        println(sum)
        val sum1 = numbers.fold(1)(_ + _)
        println(sum1)
        val sum_lef = numbers.foldLeft(1)(_ + _)
        println(sum_lef)
        val sum_right = numbers.foldRight(1)(_ + _)
        println(sum_right)
        val animalList = List(new Cat("cat"), new Animal("dog"))
        val weight_sum = animalList.foldLeft("start_")(_ + _.name)
        println(weight_sum)

        def applyTwice(f: Int => Int, x: Int): Int = f(f(x))
        println(s"对 3 应用两次 (+10): ${applyTwice(_ + 10, 3)}")

        // ========== 返回函数的函数 ==========
        def multiplier(factor: Int): Int => Int = (x: Int) => x * factor

        val triple = multiplier(3)
        val quadruple = multiplier(4)
        println(s"triple(5) = ${triple(5)}")
        println(s"quadruple(5) = ${quadruple(5)}")


        //adt
        sealed trait Shape
        case class Circle(r: Double) extends Shape

        // ========== 柯里化示例 ==========
        // 1. 普通的多参数函数
        def add(x: Int, y: Int): Int = x + y
        println(s"普通函数: add(3, 5) = ${add(3, 5)}")

        // 2. 柯里化函数定义
        def addCurried(x: Int)(y: Int): Int = x + y
        println(s"柯里化函数: addCurried(3)(5) = ${addCurried(3)(5)}")

        // 3. 使用柯里化创建部分应用函数
        val addThree: Int => Int = addCurried(3)
        println(s"部分应用函数: addThree(5) = ${addThree(5)}")
        println(s"部分应用函数: addThree(10) = ${addThree(10)}")

        // 4. 柯里化的实际应用场景
        def filterByThreshold(threshold: Int)(numbers: List[Int]): List[Int] = 
            numbers.filter(_ > threshold)

        val filterAbove5: List[Int] => List[Int] = filterByThreshold(5)
        val numbersForFilter = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        println(s"大于5的数字: ${filterAbove5(numbersForFilter)}")

        // 5. 使用curried方法将普通函数转换为柯里化函数
        val addCurriedVersion = (add _).curried
        val addFour = addCurriedVersion(4)
        println(s"使用curried方法: addFour(6) = ${addFour(6)}")

        // 6. 多参数柯里化示例
        def calculate(a: Int)(b: Int)(c: Int): Int = a * b + c
        val step1: Int => Int => Int = calculate(2)
        val step2: Int => Int = step1(3)
        println(s"多参数柯里化: calculate(2)(3)(4) = ${calculate(2)(3)(4)}")
        println(s"分步柯里化: step2(4) = ${step2(4)}")

        // ========== 柯里化的实际应用场景 ==========
        println("\n=== 实际应用场景 ===")
        
        // 场景1: 配置化的日志记录器
        def logger(level: String)(message: String)(timestamp: Long): Unit = 
            println(s"[$level][$timestamp] $message")
        
        val infoLogger: String => Long => Unit = logger("INFO")
        val errorLogger: String => Long => Unit = logger("ERROR")
        
        infoLogger("系统启动成功")(System.currentTimeMillis())
        errorLogger("数据库连接失败")(System.currentTimeMillis())
        
        // 场景2: 数据库查询构建器
        def query(table: String)(where: String)(limit: Int): String = 
            s"SELECT * FROM $table WHERE $where LIMIT $limit"
        
        val userQuery: String => Int => String = query("users")
        val activeUsers: Int => String = userQuery("status = 'active'")
        println(s"查询: ${activeUsers(10)}")
        
        // 场景3: API客户端配置
        def apiClient(baseUrl: String)(timeout: Int)(headers: Map[String, String]) = 
            (endpoint: String) => s"调用 $baseUrl/$endpoint, 超时: ${timeout}ms, 头信息: $headers"
        
        val githubClient: Int => Map[String, String] => String => String = apiClient("https://api.github.com")
        val githubWithAuth: String => String = githubClient(5000)(Map("Authorization" -> "Bearer token"))
        println(githubWithAuth("users/octocat"))
        
        // 场景4: 验证器工厂
        def validator(min: Int)(max: Int)(value: Int): Boolean = value >= min && value <= max
        
        val ageValidator: Int => Int => Boolean = validator(18)
        val scoreValidator: Int => Int => Boolean = validator(0)
        
        println(s"年龄25是否有效: ${ageValidator(120)(25)}")
        println(s"分数85是否有效: ${scoreValidator(100)(85)}")
        
        // 场景5: 货币转换器 - 修复语法
        def currencyConverter(rate: Double): Double => Double = (amount: Double) => amount * rate
        
        val usdToCny = currencyConverter(7.2)
        val eurToCny = currencyConverter(7.8)
        
        println(s"100美元 = ${usdToCny(100)}人民币")
        println(s"100欧元 = ${eurToCny(100)}人民币")

        // ========== 扩展：更完整的数据库查询构建器 ==========
        println("\n=== 扩展：完整数据库查询构建器 ===")
        
        // 完整的查询构建器 with 可选参数
        def advancedQuery(table: String)(
            where: Option[String] = None,
            orderBy: Option[String] = None,
            limit: Option[Int] = None
        ): String = {
            val baseQuery = s"SELECT * FROM $table"
            val whereClause = where.map(w => s" WHERE $w").getOrElse("")
            val orderClause = orderBy.map(o => s" ORDER BY $o").getOrElse("")
            val limitClause = limit.map(l => s" LIMIT $l").getOrElse("")
            
            baseQuery + whereClause + orderClause + limitClause
        }
        
        // 创建特定表的查询构建器
        val userQueryBuilder: (Option[String], Option[String], Option[Int]) => String = advancedQuery("users")
        val productQueryBuilder: (Option[String], Option[String], Option[Int]) => String = advancedQuery("products")
        
        // 构建各种查询
        val activeUsersQuery = userQueryBuilder(Some("status = 'active'"), None, Some(10))
        val topProductsQuery = productQueryBuilder(None, Some("price DESC"), Some(5))
        val userByIdQuery = userQueryBuilder(Some("id = 123"), None, None)
        
        println(s"活跃用户查询: $activeUsersQuery")
        println(s"热门产品查询: $topProductsQuery")
        println(s"用户ID查询: $userByIdQuery")
        
        // 链式柯里化构建器
        def queryBuilder(table: String): (Option[String] => Option[String] => Option[Int] => String) = {
            where => orderBy => limit => {
                val base = s"SELECT * FROM $table"
                val whereClause = where.map(w => s" WHERE $w").getOrElse("")
                val orderClause = orderBy.map(o => s" ORDER BY $o").getOrElse("")
                val limitClause = limit.map(l => s" LIMIT $l").getOrElse("")
                base + whereClause + orderClause + limitClause
            }
        }
        
        val buildOrderQuery = queryBuilder("orders")
        val recentOrders = buildOrderQuery(Some("status = 'completed'"))(Some("created_at DESC"))(Some(20))
        println(s"最近订单查询: $recentOrders")
    }
}