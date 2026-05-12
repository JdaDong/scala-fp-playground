package demo.part09

import cats.free.Free
import cats.free.Free.liftF
import cats.{Id, ~>}
import cats.syntax.all.*

import scala.collection.mutable

/**
 * ============================================================
 * Scene 01: Free Monad 入门 —— 把"程序"当成数据
 *
 *   核心思想：
 *     1) 把每个"指令"建模成一个 ADT（algebra）
 *     2) 用 Free 把这些指令"序列化"成一棵 AST
 *     3) for-comprehension 写出来的程序，本质上是一个数据结构
 *     4) 最后用 Interpreter (~>) 把数据结构"翻译"成真实执行
 *
 *   Free Monad 与 Tagless Final 的关系：
 *     - Tagless Final  = 用 type class 抽象效果      → 编译期解决
 *     - Free Monad     = 用 ADT + Interpreter 抽象效果 → 运行期 AST
 *     两者目标相同：让"业务"和"执行"解耦
 *     Tagless 性能更好；Free 更适合需要"先组合再优化"的场景（如 doobie 的 SQL）
 *
 *   ★ 这是 doobie / cats-mtl / 自研 DSL 的核心模式
 * ============================================================
 */
object Scene01_FreeMonadIntro {

  // ============================================================
  // ① Algebra：定义指令集
  //   每个 case 是一条"指令"，参数是输入，类型参数 A 是返回值类型
  // ============================================================
  sealed trait KVStoreA[A]
  case class Put(key: String, value: String) extends KVStoreA[Unit]
  case class Get(key: String)                extends KVStoreA[Option[String]]
  case class Delete(key: String)             extends KVStoreA[Unit]

  // ============================================================
  // ② Smart constructors：把指令包装成 Free
  //   Free[KVStoreA, A] 表示"一段可能含 KVStoreA 指令、最终产出 A 的程序"
  // ============================================================
  type KVStore[A] = Free[KVStoreA, A]

  def put(k: String, v: String): KVStore[Unit] =
    liftF[KVStoreA, Unit](Put(k, v))

  def get(k: String): KVStore[Option[String]] =
    liftF[KVStoreA, Option[String]](Get(k))

  def delete(k: String): KVStore[Unit] =
    liftF[KVStoreA, Unit](Delete(k))

  // 派生指令（基于已有指令组合而成，纯业务，不需要扩展 algebra）
  def update(k: String, f: String => String): KVStore[Unit] = for {
    v <- get(k)
    _ <- v.fold(put(k, "default"))(v0 => put(k, f(v0)))
  } yield ()

  // ============================================================
  // ③ 业务程序：一段 for-comprehension —— 此时只是个 AST，没执行
  // ============================================================
  val program: KVStore[Option[String]] = for {
    _      <- put("user:1", "Alice")
    _      <- put("user:2", "Bob")
    _      <- update("user:1", _ + "-Senior")
    _      <- delete("user:2")
    result <- get("user:1")
  } yield result

  // ============================================================
  // ④ Interpreter A：解释成"打印 + 真正存到 mutable Map"
  //   ~> 是 cats 的 FunctionK：F ~> G 表示 forall A. F[A] => G[A]
  // ============================================================
  val mutableImpureInterpreter: KVStoreA ~> Id = new (KVStoreA ~> Id) {
    val store = mutable.Map.empty[String, String]
    def apply[A](fa: KVStoreA[A]): Id[A] = fa match {
      case Put(k, v)    => println(s"    [exec] put($k, $v)");    store.update(k, v)
      case Get(k)       => println(s"    [exec] get($k)");        store.get(k)
      case Delete(k)    => println(s"    [exec] delete($k)");     store.remove(k); ()
    }
  }

  // ============================================================
  // ⑤ Interpreter B：解释成"日志列表"（不真执行）
  //   适合做"dry run"：先看看程序会做什么，再决定要不要跑
  // ============================================================
  val logs = mutable.ListBuffer.empty[String]
  val dryRunInterpreter: KVStoreA ~> Id = new (KVStoreA ~> Id) {
    def apply[A](fa: KVStoreA[A]): Id[A] = fa match {
      case Put(k, v)  => logs += s"PUT $k=$v";    ()
      case Get(k)     => logs += s"GET $k";       Some("dummy"): Option[String]
      case Delete(k)  => logs += s"DEL $k";       ()
    }
  }

  // ============================================================
  // ⑥ 运行
  // ============================================================
  def main(args: Array[String]): Unit = {
    println("===== Scene01: Free Monad 入门 =====\n")

    println("【步骤 1】写 program 时只是构造 AST，没执行任何东西")
    println(s"    program 的类型: KVStore[Option[String]]\n")

    println("【步骤 2】用 mutableImpureInterpreter 真实执行")
    val result1 = program.foldMap(mutableImpureInterpreter)
    println(s"    最终结果：$result1\n")

    println("【步骤 3】同一个 program，换一个 dryRun 解释器")
    logs.clear()
    val result2 = program.foldMap(dryRunInterpreter)
    println(s"    dry run 看到的指令序列：")
    logs.foreach(s => println(s"      - $s"))
    println(s"    返回值（用 dummy 模拟）：$result2")

    println(
      """
        |  ★ 关键观察：
        |    1. 业务作者写 program 时不用考虑"怎么执行"
        |    2. 一个程序可以有 N 个解释器（真跑 / dry run / 测试 / 优化）
        |    3. for-comprehension 写出来的代码 = 一棵可分析的 AST
        |    4. 这是 doobie 的核心：SQL 语句先攒成 ConnectionIO，最后一次性翻译
        |""".stripMargin)
    println("===== Scene01 完成 =====")
  }
}
