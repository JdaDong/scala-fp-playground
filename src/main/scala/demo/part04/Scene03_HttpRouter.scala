package demo.part04

/**
 * 场景三：HTTP 请求路由（微服务网关）
 *
 * 【业务背景】
 *   API 网关需要根据请求的 method、path、headers 将请求路由到不同的服务。
 *   典型需求：
 *     - 认证中间件要优先于业务路由（未认证直接返回 401）
 *     - 不同业务的路由规则由不同团队独立开发
 *     - 找不到匹配路由时兜底返回 404
 *
 * 【偏函数的价值】
 *   - 每个服务的路由是独立的偏函数，物理上可拆分到不同文件
 *   - orElse 天然表达"优先级"：先认证 -> 业务路由 -> 404 兜底
 *   - isDefinedAt 可在真正执行前判断"是否有路由能处理这个请求"
 */
object Scene03_HttpRouter {

  case class HttpRequest(method: String, path: String, headers: Map[String, String])
  case class HttpResponse(status: Int, body: String)

  // 认证中间件（优先级最高）
  val authMiddleware: PartialFunction[HttpRequest, HttpResponse] = {
    case req if !req.headers.contains("Authorization") =>
      HttpResponse(401, """{"error":"Unauthorized"}""")
  }

  // 用户服务路由
  val userRoutes: PartialFunction[HttpRequest, HttpResponse] = {
    case HttpRequest("GET", path, _) if path.startsWith("/api/users/") =>
      val id = path.stripPrefix("/api/users/")
      HttpResponse(200, s"""{"userId":"$id","name":"Alice"}""")

    case HttpRequest("POST", "/api/users", _) =>
      HttpResponse(201, """{"created":true}""")
  }

  // 订单服务路由
  val orderRoutes: PartialFunction[HttpRequest, HttpResponse] = {
    case HttpRequest("GET", "/api/orders", _) =>
      HttpResponse(200, """{"orders":[{"id":1},{"id":2}]}""")

    case HttpRequest("GET", path, _) if path.startsWith("/api/orders/") =>
      val id = path.stripPrefix("/api/orders/")
      HttpResponse(200, s"""{"orderId":"$id","status":"paid"}""")
  }

  // 404 兜底
  val notFound: PartialFunction[HttpRequest, HttpResponse] = {
    case req => HttpResponse(404, s"""{"error":"Not Found","path":"${req.path}"}""")
  }

  // 组合完整路由表
  val router: HttpRequest => HttpResponse =
    authMiddleware orElse userRoutes orElse orderRoutes orElse notFound

  def main(args: Array[String]): Unit = {
    println("=== 场景三：HTTP 请求路由 ===\n")

    val requests = List(
      HttpRequest("GET",  "/api/users/42",    Map("Authorization" -> "Bearer xxx")),
      HttpRequest("POST", "/api/users",       Map("Authorization" -> "Bearer xxx")),
      HttpRequest("GET",  "/api/orders",      Map("Authorization" -> "Bearer xxx")),
      HttpRequest("GET",  "/api/orders/999",  Map("Authorization" -> "Bearer xxx")),
      HttpRequest("GET",  "/api/users/1",     Map.empty),                 // 无认证 -> 401
      HttpRequest("GET",  "/unknown/path",    Map("Authorization" -> "x")) // 无匹配 -> 404
    )

    requests.foreach { req =>
      val resp = router(req)
      println(f"${req.method}%-5s ${req.path}%-25s -> ${resp.status}  ${resp.body}")
    }
  }
}
