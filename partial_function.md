[任务规划完成]
<thinking>
用户询问"偏函数能干什么"，这是一个关于Scala偏函数(PartialFunction)功能和应用场景的问题。从环境信息可以看到用户当前正在查看scala_features_applications.md文件，这个文件包含了Scala高级特性的应用场景。

我需要为用户解释偏函数的概念、功能和应用场景。偏函数是Scala中的一个重要特性，它只处理输入值的某个子集，对于不在定义域内的值会抛出异常。

让我思考一下偏函数的主要功能和应用：
1. 模式匹配的封装
2. 只处理特定输入的函数
3. 组合和链式处理
4. 在集合操作中的应用
5. Actor系统中的消息处理

由于用户已经在查看相关文档，我可以直接基于现有知识进行解释，不需要再读取文件。
</thinking>
偏函数（PartialFunction）是Scala中一个非常重要的概念，它表示只对输入值的某个子集有定义的函数。让我为您详细解释偏函数的功能和应用场景：

## 🎯 偏函数的核心功能

### 1. **选择性处理**
偏函数只处理满足特定条件的输入值，对于不在定义域内的值会抛出`MatchError`

```scala
val onlyEven: PartialFunction[Int, String] = {
  case x if x % 2 == 0 => s"$x 是偶数"
}

println(onlyEven(2))  // "2 是偶数"
// onlyEven(1)        // 抛出 MatchError
```

### 2. **模式匹配封装**
将复杂的模式匹配逻辑封装为可重用的函数单元

```scala
val processRequest: PartialFunction[HttpRequest, HttpResponse] = {
  case GetRequest("/users") => UsersResponse()
  case PostRequest("/users", data) => CreateUserResponse(data)
  case GetRequest("/users/" + id) => UserDetailResponse(id)
}
```

### 3. **组合能力**
使用`orElse`组合多个偏函数

```scala
val processA: PartialFunction[Int, String] = { case 1 => "A" }
val processB: PartialFunction[Int, String] = { case 2 => "B" }
val processC: PartialFunction[Int, String] = { case 3 => "C" }

val combined = processA orElse processB orElse processC
```

### 4. **定义域检查**
使用`isDefinedAt`检查输入是否在定义域内

```scala
val isValidInput: PartialFunction[String, Boolean] = {
  case "yes" | "no" => true
}

if (isValidInput.isDefinedAt(userInput)) {
  println("有效输入")
} else {
  println("无效输入")
}
```

## 🚀 实际应用场景

### 1. **消息处理系统** (Akka Actor)
```scala
class MyActor extends Actor {
  def receive: Receive = {
    case "start" => println("开始处理")
    case "stop" => println("停止处理")
    case data: Data => processData(data)
  }
}
// receive方法就是一个偏函数
```

### 2. **集合操作**
```scala
val numbers = List(1, 2, 3, 4, 5, "a", "b")
val evenNumbers = numbers.collect {
  case x: Int if x % 2 == 0 => x
}
// 结果: List(2, 4)
```

### 3. **路由系统** (Play Framework)
```scala
val routes: PartialFunction[Request, Result] = {
  case GET(p"/users") => UsersController.index()
  case GET(p"/users/$id") => UsersController.show(id)
  case POST(p"/users") => UsersController.create()
}
```

### 4. **数据验证和转换**
```scala
val safeParseInt: PartialFunction[String, Int] = {
  case s if s.matches("\\\\d+") => s.toInt
}

val inputs = List("123", "abc", "456")
val parsed = inputs.collect(safeParseInt)
// 结果: List(123, 456)
```

### 5. **状态机处理**
```scala
val stateTransitions: PartialFunction[(State, Event), State] = {
  case (Pending, PaymentReceived) => Paid
  case (Paid, ShipOrder) => Shipped
  case (Shipped, DeliveryConfirmed) => Delivered
}
```

## 💡 偏函数 vs 普通函数

| 特性 | 偏函数 | 普通函数 |
|------|--------|----------|
| 定义域 | 部分输入值 | 所有输入值 |
| 未定义处理 | 抛出MatchError | 需要显式处理 |
| 组合 | 使用orElse | 需要自定义组合逻辑 |
| 模式匹配 | 内置支持 | 需要额外代码 |

## 🔧 实用技巧

### 1. **使用lift方法**
将偏函数转换为返回Option的普通函数

```scala
val safeProcess = onlyEven.lift
safeProcess(2)  // Some("2 是偶数")
safeProcess(1)  // None
```

### 2. **与collect结合**
```scala
case class Person(name: String, age: Int)
val people = List(Person("Alice", 25), Person("Bob", 17), Person("Charlie", 30))

val adults = people.collect {
  case p if p.age >= 18 => p.name
}
// 结果: List("Alice", "Charlie")
```

### 3. **错误处理模式**
```scala
def processSafely[T](input: T, pf: PartialFunction[T, Unit]): Unit = {
  if (pf.isDefinedAt(input)) {
    pf(input)
  } else {
    println(s"无法处理输入: $input")
  }
}
```

偏函数让您能够编写更加声明式、安全的代码，特别适合处理需要选择性响应的场景，是现代Scala编程中不可或缺的工具。