package demo.part07

/**
 * ============================================================
 * Scene 05: 综合实战 —— 从零搭一个"可插拔"的 ETL 流水线
 *
 *   业务场景：
 *     从多种"源"（CSV、JSON、内存数据）读取记录，
 *     经过验证/转换，写入多种"汇"（控制台、文件、HTTP）。
 *
 *   传统 OOP 写法的问题：
 *     - 每加一种"源"或"汇"，都要继承一堆抽象类
 *     - 不同源/汇之间的转换要写很多样板代码
 *     - 对内置类型（如 Map、case class）无能为力
 *
 *   Type Class 写法：
 *     - 定义 Source[F[_], A] / Sink[F[_], A] 两个 type class
 *     - 每个数据源/汇是一个 given instance
 *     - 业务管道是泛型函数 → 任意源/汇组合
 *
 *   ★ 这就是 fs2 / akka-streams / Spark Dataset 等库设计的核心思想。
 * ============================================================
 */
object Scene05_PluggableETL {

  // ============================================================
  // 业务模型
  // ============================================================
  case class Record(id: Long, name: String, score: Int)

  // ============================================================
  // ① Type Class：Parser[A] —— 从字符串行解析成 A
  //    ② Type Class：Renderer[A] —— 把 A 渲染成字符串行
  // ============================================================
  trait Parser[A] {
    def parse(line: String): Either[String, A]   // 错误是值
  }

  trait Renderer[A] {
    def render(a: A): String
  }

  object Parser {
    def apply[A](using p: Parser[A]): Parser[A] = p
    def from[A](f: String => Either[String, A]): Parser[A] = (line: String) => f(line)
  }

  object Renderer {
    def apply[A](using r: Renderer[A]): Renderer[A] = r
    def from[A](f: A => String): Renderer[A] = (a: A) => f(a)
  }

  // ============================================================
  // 给 Record 提供 CSV 和 JSON 两种 instance
  // ============================================================
  object CsvFormat {
    given csvParser: Parser[Record] = Parser.from { line =>
      line.split(",").toList match {
        case idStr :: name :: scoreStr :: Nil =>
          for {
            id    <- idStr.trim.toLongOption.toRight(s"非法 id：$idStr")
            score <- scoreStr.trim.toIntOption.toRight(s"非法 score：$scoreStr")
          } yield Record(id, name.trim, score)
        case _ => Left(s"非 CSV 格式：$line")
      }
    }
    given csvRenderer: Renderer[Record] = Renderer.from(r => s"${r.id},${r.name},${r.score}")
  }

  object JsonFormat {
    // 极简 JSON（仅演示，不处理转义）
    given jsonParser: Parser[Record] = Parser.from { line =>
      val pat = """\{\s*"id"\s*:\s*(\d+)\s*,\s*"name"\s*:\s*"([^"]*)"\s*,\s*"score"\s*:\s*(\d+)\s*\}""".r
      line match {
        case pat(id, name, score) => Right(Record(id.toLong, name, score.toInt))
        case _                    => Left(s"非 JSON 格式：$line")
      }
    }
    given jsonRenderer: Renderer[Record] = Renderer.from(r =>
      s"""{"id":${r.id},"name":"${r.name}","score":${r.score}}"""
    )
  }

  // ============================================================
  // ③ Type Class：Source[A] —— 抽象"输入"
  //    ④ Type Class：Sink[A]   —— 抽象"输出"
  // ============================================================
  trait Source[A] {
    def read(): Iterator[A]
  }

  trait Sink[A] {
    def write(a: A): Unit
    def close(): Unit = ()
  }

  // ============================================================
  // 通用工厂：从一组"行"+ Parser → 出一个 Source
  // ============================================================
  def linesSource[A: Parser](lines: List[String]): Source[A] = new Source[A] {
    def read(): Iterator[A] = lines.iterator.flatMap { line =>
      Parser[A].parse(line) match {
        case Right(a)  => Iterator.single(a)
        case Left(err) => println(s"    [warn] 跳过：$err"); Iterator.empty
      }
    }
  }

  // 内存 Source（已经是 case class，不需要 Parser）
  def memorySource[A](xs: List[A]): Source[A] = new Source[A] {
    def read(): Iterator[A] = xs.iterator
  }

  // ============================================================
  // 通用工厂：从 Renderer → 出一个 Sink
  // ============================================================
  def consoleSink[A: Renderer](label: String): Sink[A] = new Sink[A] {
    def write(a: A): Unit = println(s"    [$label] ${Renderer[A].render(a)}")
  }

  // 收集到 ListBuffer 的 Sink（方便测试）
  def collectingSink[A: Renderer](): (Sink[A], () => List[String]) = {
    val buf = scala.collection.mutable.ListBuffer.empty[String]
    val sink = new Sink[A] {
      def write(a: A): Unit = buf += Renderer[A].render(a)
    }
    (sink, () => buf.toList)
  }

  // ============================================================
  // ⑤ 业务管道：一个泛型 ETL，源/汇都可插拔
  // ============================================================
  def etl[A](source: Source[A], sink: Sink[A], filter: A => Boolean = (_: A) => true): Int = {
    var count = 0
    try {
      source.read().filter(filter).foreach { a =>
        sink.write(a)
        count += 1
      }
    } finally sink.close()
    count
  }

  // ============================================================
  // 演示：同一段 etl，用不同源和汇组合
  // ============================================================
  def main(args: Array[String]): Unit = {
    println("===== Scene05: 可插拔 ETL 实战 =====\n")

    // ---- 测试数据 ----
    val csvLines = List(
      "1, Alice, 95",
      "2, Bob, 72",
      "bad row",                  // 故意写一行错的
      "3, Charlie, 88"
    )
    val jsonLines = List(
      """{"id":10,"name":"Dave","score":60}""",
      """{"id":11,"name":"Eve","score":91}""",
      """malformed""",
      """{"id":12,"name":"Frank","score":78}"""
    )
    val memRecords = List(
      Record(100, "Memory-A", 99),
      Record(101, "Memory-B", 50)
    )

    // ============================================================
    // 组合 1：CSV → 控制台（CSV 渲染）
    // ============================================================
    println("【组合 1】CSV 源 → 控制台（CSV 渲染）")
    {
      import CsvFormat.given
      val src  = linesSource[Record](csvLines)
      val sink = consoleSink[Record]("CSV-OUT")
      val n    = etl(src, sink)
      println(s"    成功导出 $n 条\n")
    }

    // ============================================================
    // 组合 2：JSON → 控制台（JSON 渲染）+ 过滤分数 >= 80
    // ============================================================
    println("【组合 2】JSON 源 → 控制台（JSON 渲染）+ 过滤 score >= 80")
    {
      import JsonFormat.given
      val src  = linesSource[Record](jsonLines)
      val sink = consoleSink[Record]("JSON-OUT")
      val n    = etl(src, sink, _.score >= 80)
      println(s"    成功导出 $n 条\n")
    }

    // ============================================================
    // 组合 3：跨格式 ETL —— CSV 进，JSON 出
    //   这里完全不需要写转换代码，只要 import 不同的 Format
    // ============================================================
    println("【组合 3】CSV 源 → JSON 渲染（跨格式自动转换）")
    {
      // 解析端用 CSV
      import CsvFormat.csvParser
      val src = linesSource[Record](csvLines)

      // 渲染端用 JSON
      import JsonFormat.jsonRenderer
      val sink = consoleSink[Record]("CSV→JSON")
      val n    = etl(src, sink)
      println(s"    成功转换 $n 条\n")
    }

    // ============================================================
    // 组合 4：内存源（不需要 Parser）→ 收集型 Sink
    // ============================================================
    println("【组合 4】内存源 → 收集到 List")
    {
      import JsonFormat.jsonRenderer
      val src              = memorySource(memRecords)
      val (sink, getLines) = collectingSink[Record]()
      etl(src, sink)
      getLines().foreach(s => println(s"    [collected] $s"))
    }

    println("\n  ★ 关键观察：")
    println("    1. 同一个 etl 函数适用所有源/汇组合")
    println("    2. 加新格式（XML / Protobuf）只要写两个 given：Parser + Renderer")
    println("    3. 加新源/汇（HTTP / Kafka / S3）只要实现 Source / Sink 接口")
    println("    4. 整体代码完全是\"组合 + 选择\"，没有任何继承/反射")

    println("\n===== Scene05 完成 =====")
  }
}
