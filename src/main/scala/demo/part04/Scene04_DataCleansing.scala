package demo.part04

/**
 * 场景四：数据清洗管道（ETL）
 *
 * 【业务背景】
 *   ETL 场景下原始数据来源复杂（日志、第三方 API、爬虫等），
 *   同一列可能出现多种类型：整数、浮点数、字符串形式的数字、null、对象……
 *   需求：
 *     - 只保留"能被转为 Double"的值
 *     - 其他不合规数据（null、Map、List 等）直接丢弃
 *
 * 【偏函数的价值】
 *   - collect 方法是专门为偏函数设计的：只保留匹配的值，并同时做类型转换
 *   - 比传统 filter + map + cast 三步走更简洁、更类型安全
 *   - 清洗规则与处理流程解耦，易于测试和复用
 */
object Scene04_DataCleansing {

  // 数据清洗偏函数：只处理"能转 Double"的值
  val dataCleaner: PartialFunction[Any, Double] = {
    case i: Int                                       => i.toDouble
    case l: Long                                      => l.toDouble
    case d: Double                                    => d
    case f: Float                                     => f.toDouble
    case s: String if s.matches("-?\\d+(\\.\\d+)?")   => s.toDouble
  }

  // 另一个清洗规则：只处理非空字符串，并 trim
  val stringCleaner: PartialFunction[Any, String] = {
    case s: String if s.trim.nonEmpty => s.trim.toLowerCase
  }

  def main(args: Array[String]): Unit = {
    println("=== 场景四：数据清洗管道 ===\n")

    val rawData: List[Any] = List(
      "123",                      // 合法：字符串数字
      "abc",                      // 脏数据：非数字字符串
      456,                        // 合法：Int
      null,                       // 脏数据：null
      "78.9",                     // 合法：字符串小数
      3.14,                       // 合法：Double
      Map("key" -> "value"),      // 脏数据：Map
      List(1, 2),                 // 脏数据：List
      100L,                       // 合法：Long
      ""                          // 脏数据：空字符串
    )

    // 方式 1：传统写法（冗长）
    val traditional = rawData
      .filter {
        case _: Int | _: Long | _: Double | _: Float => true
        case s: String => s.matches("-?\\d+(\\.\\d+)?")
        case _ => false
      }
      .map {
        case i: Int    => i.toDouble
        case l: Long   => l.toDouble
        case d: Double => d
        case f: Float  => f.toDouble
        case s: String => s.toDouble
      }

    // 方式 2：偏函数 + collect（推荐）
    val cleaned = rawData.collect(dataCleaner)

    println(s"原始数据 (${rawData.size} 条): $rawData")
    println(s"传统写法  (${traditional.size} 条): $traditional")
    println(s"偏函数写法 (${cleaned.size} 条): $cleaned")

    // 统计
    println(s"\n合计清洗出 ${cleaned.size} 条合法数据")
    println(s"总和 = ${cleaned.sum}, 平均 = ${cleaned.sum / cleaned.size}")

    // 复用清洗器：用于字符串
    val rawStrs: List[Any] = List("  Hello ", 123, "", "  WORLD  ", null, "scala")
    println(s"\n字符串清洗结果: ${rawStrs.collect(stringCleaner)}")
  }
}
