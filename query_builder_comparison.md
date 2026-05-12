# 🗄️ 查询构建器模式对比

| 模式 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **基础柯里化构建器** | 类型安全、函数式风格、编译时检查 | 参数顺序固定、缺乏灵活性 | 简单查询、参数固定的场景 |
| **高级可选参数构建器** | 参数可选、默认值支持、使用方便 | 类型注解较复杂、参数顺序重要 | 中等复杂度查询、需要可选参数 |
| **链式柯里化构建器** | 完全灵活、任意顺序组合、极致类型安全 | 实现复杂、学习曲线较陡 | 复杂查询构建、需要最大灵活性 |

## 📋 具体示例代码

### 1. 基础柯里化构建器
```scala
// 定义
def query(table: String)(where: String)(limit: Int): String = 
    s"SELECT * FROM $table WHERE $where LIMIT $limit"

// 使用
val userQuery: String => Int => String = query("users")
val activeUsers: Int => String = userQuery("status = 'active'")
val result: String = activeUsers(10)
// 输出: SELECT * FROM users WHERE status = 'active' LIMIT 10
```

### 2. 高级可选参数构建器
```scala
// 定义
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

// 使用
val userQueryBuilder: (Option[String], Option[String], Option[Int]) => String = 
    advancedQuery("users")

val query1 = userQueryBuilder(Some("status = 'active'"), None, Some(10))
val query2 = userQueryBuilder(None, Some("name ASC"), None)
// 输出1: SELECT * FROM users WHERE status = 'active' LIMIT 10
// 输出2: SELECT * FROM users ORDER BY name ASC
```

### 3. 链式柯里化构建器
```scala
// 定义
def queryBuilder(table: String): (Option[String] => Option[String] => Option[Int] => String) = {
    where => orderBy => limit => {
        val base = s"SELECT * FROM $table"
        val whereClause = where.map(w => s" WHERE $w").getOrElse("")
        val orderClause = orderBy.map(o => s" ORDER BY $o").getOrElse("")
        val limitClause = limit.map(l => s" LIMIT $l").getOrElse("")
        base + whereClause + orderClause + limitClause
    }
}

// 使用
val buildOrderQuery = queryBuilder("orders")
val recentOrders = buildOrderQuery(Some("status = 'completed'"))(Some("created_at DESC"))(Some(20))
// 输出: SELECT * FROM orders WHERE status = 'completed' ORDER BY created_at DESC LIMIT 20
```

## 🎯 选择建议

- **简单场景**：使用基础柯里化构建器，代码简洁明了
- **中等复杂度**：使用高级可选参数构建器，平衡灵活性和简洁性  
- **复杂场景**：使用链式柯里化构建器，获得最大灵活性和类型安全性

所有示例代码都已经在您的[demo01.scala](/Users/jiangdadong/CodeBuddy/scala-fp-playground/src/main/scala/demo/part01/demo01.scala)文件中实现并可以运行测试。