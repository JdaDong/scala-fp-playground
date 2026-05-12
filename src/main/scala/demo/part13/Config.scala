package demo.part13

/**
 * ============================================================
 * 配置 —— 通过 cats-mtl 的 Ask[F, AppConfig] 注入业务层
 *
 *   真实项目会用 pureconfig / ciris 从 application.conf 读取，
 *   这里为了自包含，直接写默认值。
 * ============================================================
 */
final case class DbConfig(
  url:      String,
  user:     String,
  password: String,
  poolSize: Int
)

final case class ServerConfig(
  host: String,
  port: Int
)

final case class BizConfig(
  maxTitleLength: Int
)

final case class AppConfig(
  db:     DbConfig,
  server: ServerConfig,
  biz:    BizConfig
)

object AppConfig {
  val default: AppConfig = AppConfig(
    db = DbConfig(
      url      = "jdbc:h2:mem:todos;DB_CLOSE_DELAY=-1",  // 进程内共享内存库
      user     = "sa",
      password = "",
      poolSize = 8
    ),
    server = ServerConfig(host = "localhost", port = 8080),
    biz    = BizConfig(maxTitleLength = 140)
  )
}
