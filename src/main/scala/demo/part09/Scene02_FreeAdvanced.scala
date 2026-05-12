package demo.part09

import cats.{Id, ~>}
import cats.data.{EitherK, State}
import cats.free.Free
import cats.InjectK
import cats.syntax.all.*

/**
 * ============================================================
 * Scene 02: Free Monad 进阶 —— 多 algebra 组合 + 静态分析
 *
 *   现实业务往往需要多种能力：缓存 + 日志 + ...
 *   Free 通过 Coproduct（EitherK）+ InjectK 优雅地组合多个 algebra：
 *
 *      type App[A] = EitherK[CacheA, LogA, A]
 *
 *   配合 InjectK，写业务时各 algebra 仿佛"自动混合"。
 *
 *   更妙的是：因为 program 是数据结构，
 *     可以写一个 "analyzer"：先扫一遍 AST，做去重/合并/统计，
 *     再交给真实解释器执行 —— 这是 Tagless Final 做不到的。
 * ============================================================
 */
object Scene02_FreeAdvanced {

  // ============================================================
  // ① 两个独立的 algebra
  // ============================================================
  sealed trait CacheA[A]
  case class CacheGet(key: String)             extends CacheA[Option[String]]
  case class CacheSet(key: String, v: String)  extends CacheA[Unit]

  sealed trait LogA[A]
  case class Log(msg: String) extends LogA[Unit]

  // ============================================================
  // ② 用 EitherK 组合两个 algebra
  // ============================================================
  type App[A]  = EitherK[CacheA, LogA, A]
  type Prog[A] = Free[App, A]

  // ============================================================
  // ③ Smart constructors —— 用 InjectK 让 lift 更优雅
  // ============================================================
  class CacheOps[F[_]](using I: InjectK[CacheA, F]) {
    def get(k: String): Free[F, Option[String]] = Free.inject[CacheA, F](CacheGet(k))
    def set(k: String, v: String): Free[F, Unit] = Free.inject[CacheA, F](CacheSet(k, v))
  }

  class LogOps[F[_]](using I: InjectK[LogA, F]) {
    def log(msg: String): Free[F, Unit] = Free.inject[LogA, F](Log(msg))
  }

  given cacheOps: CacheOps[App] = new CacheOps[App]
  given logOps:   LogOps[App]   = new LogOps[App]

  // ============================================================
  // ④ 业务程序
  // ============================================================
  def fetchOrCache(key: String, fetch: => String)
                  (using c: CacheOps[App], l: LogOps[App]): Prog[String] = for {
    _   <- l.log(s"查询 key=$key")
    opt <- c.get(key)
    v   <- opt match {
             case Some(v) =>
               l.log(s"  缓存命中：$v").as(v)
             case None    =>
               val fresh = fetch
               l.log(s"  缓存未命中，回源：$fresh") *>
                 c.set(key, fresh).as(fresh)
           }
  } yield v

  val program: Prog[List[String]] = for {
    a <- fetchOrCache("user:1", "Alice")
    b <- fetchOrCache("user:1", "ShouldNot")
    c <- fetchOrCache("user:2", "Bob")
  } yield List(a, b, c)

  // ============================================================
  // ⑤ Interpreters：先抽出 type lambda 提高可读性
  // ============================================================
  type CacheState[A] = State[Map[String, String], A]

  val cacheToState: CacheA ~> CacheState = new (CacheA ~> CacheState) {
    def apply[A](fa: CacheA[A]): CacheState[A] = fa match {
      case CacheGet(k)    => State.inspect(_.get(k))
      case CacheSet(k, v) => State.modify(_.updated(k, v))
    }
  }

  val logToState: LogA ~> CacheState = new (LogA ~> CacheState) {
    def apply[A](fa: LogA[A]): CacheState[A] = fa match {
      case Log(msg) => State.pure { println(s"    [log] $msg"); () }
    }
  }

  // 把两个解释器合并成一个 App ~> CacheState
  val combined: App ~> CacheState = cacheToState or logToState

  // ============================================================
  // ⑥ Analyzer：扫一遍 AST 做静态统计（不执行业务）
  //    给类型 lambda 起个名字最稳
  // ============================================================
  case class Stats(cacheGets: Int, cacheSets: Int, logs: Int)
  type StatState[A] = State[Stats, A]

  val analyzer: App ~> StatState = new (App ~> StatState) {
    def apply[A](fa: App[A]): StatState[A] = fa.run match {
      case Left(c) => c match {
        case CacheGet(_) =>
          // CacheGet 的 A = Option[String]，需要返回 Option[String]
          State.modify[Stats](s => s.copy(cacheGets = s.cacheGets + 1))
            .as(Option.empty[String]).asInstanceOf[StatState[A]]
        case CacheSet(_, _) =>
          State.modify[Stats](s => s.copy(cacheSets = s.cacheSets + 1)).asInstanceOf[StatState[A]]
      }
      case Right(Log(_)) =>
        State.modify[Stats](s => s.copy(logs = s.logs + 1)).asInstanceOf[StatState[A]]
    }
  }

  // ============================================================
  // ⑦ 运行
  // ============================================================
  def main(args: Array[String]): Unit = {
    println("===== Scene02: Free Monad 进阶 =====\n")

    println("【1】用真实解释器跑程序")
    val (finalCache, result) = program.foldMap(combined).run(Map.empty).value
    println(s"    返回：$result")
    println(s"    最终缓存：$finalCache\n")

    println("【2】用 analyzer 静态扫描（不执行业务，只统计）")
    val (stats, _) = program.foldMap(analyzer).run(Stats(0, 0, 0)).value
    println(s"    程序里包含: $stats")
    println(s"    （这 = 静态分析，OOP 完全做不到）")

    println(
      """
        |  ★ 关键观察：
        |    1. EitherK + InjectK 让多个 algebra 自然组合
        |    2. 业务代码读起来仍像 for-comprehension
        |    3. ★ 同一个 program 既能"运行"也能"被分析"
        |       这是 Free Monad 比 Tagless Final 多出来的能力
        |    4. doobie 用这招把 SQL 语句先组合成 ConnectionIO，
        |       数据库驱动可以批量优化执行
        |""".stripMargin)
    println("===== Scene02 完成 =====")
  }
}
