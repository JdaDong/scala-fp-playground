package demo.part13

import scala.concurrent.duration.*

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.{Host, Port}

import doobie.*
import doobie.hikari.HikariTransactor

import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger as Http4sLogger

/**
 * ============================================================
 * Main —— 入口：装配所有组件
 *
 *   启动顺序：
 *     1) 构造 HikariTransactor（数据库连接池 Resource）
 *     2) 构造 TodoRepo + 执行 DDL
 *     3) 装配 http4s 路由
 *     4) 启动 EmberServer
 *
 *   所有资源通过 Resource / use 管理，保证优雅退出。
 *
 *   启动后可用命令测试：
 *     curl http://localhost:8080/health
 *     curl -X POST http://localhost:8080/todos -d '{"title":"Learn http4s"}' -H 'Content-Type: application/json'
 *     curl http://localhost:8080/todos
 * ============================================================
 */
object Main extends IOApp {

  // ============================================================
  // 1. 数据库连接池（Resource —— 关服务时自动释放）
  // ============================================================
  private def transactor(cfg: DbConfig): Resource[IO, HikariTransactor[IO]] =
    for {
      ec <- doobie.util.ExecutionContexts.fixedThreadPool[IO](cfg.poolSize)
      xa <- HikariTransactor.newHikariTransactor[IO](
              driverClassName = "org.h2.Driver",
              url             = cfg.url,
              user            = cfg.user,
              pass            = cfg.password,
              connectEC       = ec
            )
    } yield xa

  // ============================================================
  // 2. 启动 http4s Ember server
  // ============================================================
  private def server(
      cfg:  ServerConfig,
      routes: org.http4s.HttpRoutes[IO]
  ): Resource[IO, org.http4s.server.Server] = {
    val app = Http4sLogger.httpApp[IO](
      logHeaders = false, logBody = true
    )(routes.orNotFound)

    EmberServerBuilder.default[IO]
      .withHost(Host.fromString(cfg.host).get)
      .withPort(Port.fromInt(cfg.port).get)
      .withHttpApp(app)
      .withShutdownTimeout(1.second)
      .build
  }

  // ============================================================
  // 3. 入口
  // ============================================================
  override def run(args: List[String]): IO[ExitCode] = {
    val cfg = AppConfig.default

    val app: Resource[IO, org.http4s.server.Server] =
      for {
        xa    <- transactor(cfg.db)
        repo  =  TodoRepo(xa)
        _     <- Resource.eval(repo.initSchema)
        _     <- Resource.eval(IO.println(
                   s"\n  ✅ Todo 服务启动：http://${cfg.server.host}:${cfg.server.port}\n" +
                   s"  试试：\n" +
                   s"    curl http://${cfg.server.host}:${cfg.server.port}/health\n" +
                   s"    curl -X POST http://${cfg.server.host}:${cfg.server.port}/todos " +
                   s"-d '{\"title\":\"Learn http4s\"}' -H 'Content-Type: application/json'\n" +
                   s"    curl http://${cfg.server.host}:${cfg.server.port}/todos\n" +
                   s"  按 Ctrl-C 退出\n"
                 ))
        srv   <- server(cfg.server, TodoRoutes.routes(cfg, repo))
      } yield srv

    app.useForever.as(ExitCode.Success)
  }
}
