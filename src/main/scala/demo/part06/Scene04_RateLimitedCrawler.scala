package demo.part06

import cats.effect.{IO, IOApp, ExitCode, Ref, Resource}
import cats.effect.std.{Queue, Semaphore}
import cats.syntax.all.*
import scala.concurrent.duration.*

/**
 * ============================================================
 * Scene 04: 综合实战 —— 一个"带速率限制 + 重试 + 资源回收"的爬虫
 *
 *   场景：并发抓取 N 个 URL，要求：
 *     ✅ 全局并发不超过 3（Semaphore 限流）
 *     ✅ 失败自动重试 3 次（IO.retry 模式）
 *     ✅ 每个请求超时 1 秒
 *     ✅ 用 Ref 累积成功 / 失败计数（线程安全）
 *     ✅ 用 Resource 管理 HTTP 客户端（保证关闭）
 *     ✅ 主程序按 Ctrl+C 时所有 fiber 都被取消，连接被释放
 *
 *   这是 Cats Effect 的"日常工作量"：把 5 个独立能力组合到一起，
 *   而不是每个能力都自己造轮子。
 * ============================================================
 */
object Scene04_RateLimitedCrawler extends IOApp {

  // ============================================================
  // 模拟 HTTP 客户端（Resource 管理生命周期）
  // ============================================================
  case class HttpClient(name: String) {
    // 模拟：偶发失败 + 随机延迟
    def fetch(url: String): IO[String] = IO.defer {
      val delay = (100 + scala.util.Random.nextInt(400)).millis
      val fail  = scala.util.Random.nextDouble() < 0.4   // 40% 失败率
      IO.sleep(delay) *> {
        if (fail) IO.raiseError(new RuntimeException(s"network error: $url"))
        else      IO.pure(s"<html>$url</html>")
      }
    }
  }

  val httpClient: Resource[IO, HttpClient] =
    Resource.make(
      IO.println("    >> 启动 HttpClient").as(HttpClient("client-1"))
    )(_ => IO.println("    << 关闭 HttpClient"))

  // ============================================================
  // 通用重试组合子：retry(io, n, delay)
  //   失败 → 等 delay → 重试，最多 n 次
  // ============================================================
  def retry[A](io: IO[A], maxAttempts: Int, delay: FiniteDuration): IO[A] =
    io.handleErrorWith { e =>
      if (maxAttempts <= 1) IO.raiseError(e)
      else IO.sleep(delay) *> retry(io, maxAttempts - 1, delay)
    }

  // ============================================================
  // 单个 URL 的抓取：限流 + 重试 + 超时 + 计数
  // ============================================================
  case class Stats(success: Int, failure: Int) {
    def +(r: Either[Throwable, ?]): Stats =
      r.fold(_ => copy(failure = failure + 1), _ => copy(success = success + 1))
  }

  def fetchOne(
    client: HttpClient,
    url:    String,
    sem:    Semaphore[IO],
    stats:  Ref[IO, Stats]
  ): IO[Unit] =
    sem.permit.use { _ =>                       // 限流：拿不到许可就排队
      val once = client.fetch(url).timeout(1.second)
      retry(once, maxAttempts = 3, delay = 200.millis)
        .attempt                                // 把异常变 Either
        .flatTap {                              // 计数（不影响结果）
          case Right(_) => IO.println(s"    ✅ $url")
          case Left(e)  => IO.println(s"    ❌ $url ：${e.getMessage}")
        }
        .flatMap(r => stats.update(_ + r))
    }

  // ============================================================
  // 主流程
  // ============================================================
  def crawl(urls: List[String]): IO[Stats] =
    httpClient.use { client =>
      for {
        sem   <- Semaphore[IO](3)               // 全局并发 3
        stats <- Ref.of[IO, Stats](Stats(0, 0)) // 线程安全计数器
        _     <- urls.parTraverse_(url =>       // 并行抓取，自动按 sem 限流
                   fetchOne(client, url, sem, stats)
                 )
        final_ <- stats.get
      } yield final_
    }

  def run(args: List[String]): IO[ExitCode] = {
    val urls = (1 to 12).map(i => s"https://site/page$i").toList

    for {
      _      <- IO.println("===== Scene04: 限流 + 重试 + 资源回收 实战 =====")
      _      <- IO.println(s"    目标：${urls.size} 个 URL，全局并发 3，每个最多 3 次重试")
      start  <- IO.monotonic
      stats  <- crawl(urls)
      end    <- IO.monotonic
      _      <- IO.println("")
      _      <- IO.println(s"    成功：${stats.success}，失败：${stats.failure}")
      _      <- IO.println(s"    总耗时：${(end - start).toMillis} ms")
      _      <- IO.println("===== Scene04 完成 =====")
    } yield ExitCode.Success
  }
}
