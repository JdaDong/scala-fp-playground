# 🚀 项目运行指南（How to Run）

> 项目名：**`scala-fp-playground`**（Scala 函数式编程练习场）
>
> 一份从 **零配置** 到 **跑通全部 41 个 demo** 的实操手册。
> 配套详细入口列表请见 [`RUN.md`](./RUN.md)。

---

## 0. 项目是什么

这是一个 **Scala 3 + 函数式编程** 的学习项目，包含 13 个模块（part01 ~ part13）、共 **41 个可独立运行的 demo**，覆盖：

| 模块 | 主题 |
|---|---|
| part01 ~ part03 | Scala 基础语法、柯里化、高级特性 |
| part04 | 偏函数 8 大应用场景 |
| part05 | for 推导式 + 模式匹配 + Validated |
| part06 | Cats Effect（IO / 取消 / 资源） |
| part07 | Type Class 模式 |
| part08 | Tagless Final |
| part09 | Free Monad |
| part10 | fs2 Stream |
| part11 | HKT + Variance |
| part12 | cats-mtl |
| **part13** | **http4s + doobie + cats-mtl REST 服务（集大成）** |

---

## 1. 环境准备（一次性）

### 1.1 必备

| 工具 | 版本 | 校验命令 |
|---|---|---|
| **JDK** | 17+（推荐 21 / 26） | `java -version` |
| **sbt** | 1.9+（项目用 1.12.8） | `sbt --version` |

### 1.2 macOS 安装（用 Homebrew）

```bash
brew install openjdk@21
brew install sbt
```

### 1.3 校验环境

```bash
java -version       # 应显示 17 / 21 / 26
sbt --version       # 应显示 sbt runner version: 1.x
```

---

## 2. 第一次运行（30 秒上手）

```bash
# Step 1: 进入项目目录
cd /Users/jiangdadong/CodeBuddy/scala-fp-playground

# Step 2: 跑一个最简单的 demo，确认环境 OK
sbt "runMain demo.part02.CurryingExamples"
```

第一次运行 sbt 会**自动下载所有依赖**（cats、cats-effect、http4s、doobie、fs2…），约 **3~10 分钟**，**只有第一次慢**，之后都很快。

> ✅ 看到类似 `[success] Total time: 4 s` 就说明跑通了。

---

## 3. 三种运行方式（按场景选）

### 方式 A：单跑某个 demo（最常用）

```bash
sbt "runMain <完整类路径>"
```

例如：

```bash
sbt "runMain demo.part01.demo01"
sbt "runMain demo.part10.Scene01_FS2Intro"
sbt "runMain demo.part12.Scene03_TwoInterpreters"
```

完整类路径列表见 [`RUN.md`](./RUN.md)。

---

### 方式 B：⚡ 一键串跑全部 demo（推荐！）

项目里已经在 `build.sbt` 写好了一个自定义 task：

```bash
sbt runAllDemos
```

它会**按顺序跑完 part01 ~ part12 全部 29 个非阻塞 demo**，每个跑在独立 JVM 中，最后给汇总：

```
======================================================================
🚀 开始串跑 29 个 demo（已跳过 3 个阻塞型入口）
======================================================================
>>> [1/29]  demo.part01.demo01                       ✓   64 ms
>>> [2/29]  demo.part02.CurryingExamples             ✓    8 ms
>>> [3/29]  demo.part03.AdvancedScalaFeatures        ✓ 1528 ms
... ...
>>> [29/29] demo.part12.Scene03_TwoInterpreters      ✓  410 ms
======================================================================
📊 汇总
======================================================================
  通过：29 / 29
  总耗时：16 秒
======================================================================
```

> 跳过的 3 个阻塞型入口（需手动跑）：
> - `part06.Scene04_RateLimitedCrawler` —— 耗时较长
> - `part10.Scene04_FS2EventBus` —— 长阻塞
> - `part13.Main` —— 永久阻塞 HTTP 服务

---

### 方式 C：交互式 sbt shell（最快）

每次 `sbt "runMain ..."` 都要 5~10 秒启动 JVM，连续跑多个就太慢了。**进 sbt shell** 可以省掉这个开销：

```bash
sbt
```

看到 `sbt:scala-fp-playground>` 提示符后，可以连续跑：

```
sbt:scala-fp-playground> runMain demo.part12.Scene01_MTPain
sbt:scala-fp-playground> runMain demo.part12.Scene02_CatsMtlIntro
sbt:scala-fp-playground> runMain demo.part12.Scene03_TwoInterpreters
sbt:scala-fp-playground> runAllDemos
sbt:scala-fp-playground> exit
```

> 💡 在 sbt shell 里第二次运行同一个 demo 几乎是瞬时的（编译缓存）。

---

## 4. 启动 REST 服务（part13）🌐

`part13` 是项目里**唯一一个长阻塞型 demo**，启动后会一直运行，需要 Ctrl+C 停止。

### 4.1 启动服务

```bash
sbt "runMain demo.part13.Main"
```

看到下面的输出说明服务已就绪：

```
[INFO] Ember-Server service bound to address: [::]:8080
🚀 Server started at http://localhost:8080
```

### 4.2 在另一个终端测试

```bash
# 健康检查
curl http://localhost:8080/health

# 创建 todo
curl -X POST http://localhost:8080/todos \
  -H 'Content-Type: application/json' \
  -d '{"title":"Learn Scala FP"}'

# 查询所有
curl http://localhost:8080/todos

# 查询单个
curl http://localhost:8080/todos/1

# 更新（标记完成）
curl -X PATCH http://localhost:8080/todos/1 \
  -H 'Content-Type: application/json' \
  -d '{"completed":true}'

# 删除
curl -X DELETE http://localhost:8080/todos/1
```

### 4.3 停止服务

回到第一个终端，按 **Ctrl+C**。

---

## 5. 入口速查表

完整 41 个入口请看 [`RUN.md`](./RUN.md)，下面是一些**亮点 demo**（按学习顺序）：

| 推荐顺序 | 命令 | 学到什么 |
|---|---|---|
| 1️⃣ | `sbt "runMain demo.part01.demo01"` | Scala 语法基础 |
| 2️⃣ | `sbt "runMain demo.part04.ScenesRunner"` | 偏函数 8 大场景一次性看完 |
| 3️⃣ | `sbt "runMain demo.part05.Scene01_ForComprehension"` | for + Option/Either/Future |
| 4️⃣ | `sbt "runMain demo.part06.Scene01_FutureVsIO"` | 为什么要用 IO 替代 Future |
| 5️⃣ | `sbt "runMain demo.part07.Scene01_WhyTypeClass"` | Type Class 比 OOP 强在哪 |
| 6️⃣ | `sbt "runMain demo.part08.Scene01_FromConcreteToTagless"` | Tagless Final 的演进过程 |
| 7️⃣ | `sbt "runMain demo.part10.Scene01_FS2Intro"` | fs2 流式处理 |
| 8️⃣ | `sbt "runMain demo.part11.Scene01_HKTBasics"` | 高阶类型 |
| 9️⃣ | `sbt "runMain demo.part12.Scene02_CatsMtlIntro"` | cats-mtl 多 effect 组合 |
| 🔟 | `sbt "runMain demo.part13.Main"` + curl | **集大成 REST 服务** |

---

## 6. 常见问题（FAQ）

### Q1：第一次 `sbt` 卡了 5 分钟没动静？

**A**：sbt 在下载依赖（cats、http4s、doobie 等十几个库），首次约 100~300 MB，请耐心等待。可以在另一个终端用 `du -sh ~/.ivy2` 看下载进度。

---

### Q2：报错 `not found: demo.partXX.SceneYY`？

**A**：检查类路径是否拼对。所有入口必须用**完整包名**：

```bash
# ✓ 正确
sbt "runMain demo.part12.Scene03_TwoInterpreters"

# ✗ 错误
sbt "runMain Scene03_TwoInterpreters"
sbt "runMain demo.Scene03_TwoInterpreters"
```

或者用 sbt 自动列出所有入口：

```
sbt> show discoveredMainClasses
```

---

### Q3：跑 `part13.Main` 后端口被占用？

**A**：服务默认监听 8080。先杀掉占用进程：

```bash
lsof -ti:8080 | xargs kill -9
```

或修改 [Config.scala](./src/main/scala/demo/part13/Config.scala) 里的端口。

---

### Q4：如何只跑某个 part 的所有 demo？

**A**：在 sbt shell 里用分号串：

```
sbt> ;runMain demo.part10.Scene01_FS2Intro ;runMain demo.part10.Scene02_FS2Backpressure ;runMain demo.part10.Scene03_FS2Pipeline
```

---

### Q5：编译慢怎么办？

**A**：
1. **不要每次都退出 sbt**，进 sbt shell 连续用，第二次编译会复用缓存。
2. 用 `sbt ~compile` 启动**增量编译监听**，文件一改自动重新编译。
3. 项目用 Scala 3.3.7 + 33 个依赖库，干净编译约 1~2 分钟，正常现象。

---

### Q6：怎么查所有可运行入口？

```bash
sbt
sbt> show discoveredMainClasses
```

或直接看 [`RUN.md`](./RUN.md)。

---

### Q7：阻塞型 demo 怎么停？

| Demo | 停止方式 |
|---|---|
| `part06.Scene04_RateLimitedCrawler` | 自动结束（约 10 秒） |
| `part10.Scene04_FS2EventBus` | 自动结束（约 30 秒） |
| `part13.Main` | **Ctrl + C** |

---

## 7. 进阶：开发模式

### 7.1 文件改动后自动重编译

```
sbt> ~compile
```

按 Enter 退出监听。

### 7.2 文件改动后自动重跑某 demo

```
sbt> ~runMain demo.part12.Scene03_TwoInterpreters
```

### 7.3 跑所有测试（项目目前没有测试，可后续添加）

```bash
sbt test
```

### 7.4 列出所有 sbt task

```
sbt> tasks -V
```

可以看到我们自定义的 `runAllDemos`。

---

## 8. 相关文档

| 文档 | 主题 |
|---|---|
| [`RUN.md`](./RUN.md) | 完整 41 个入口的运行命令 |
| [`HOW_TO_RUN.md`](./HOW_TO_RUN.md) | **本文件**：新手向运行指南 |
| [`scala_features_applications.md`](./scala_features_applications.md) | Scala 高级特性应用场景 |

---

## 9. 一句话速查

```bash
# 跑一个：
sbt "runMain demo.part12.Scene03_TwoInterpreters"

# 跑全部：
sbt runAllDemos

# 起服务：
sbt "runMain demo.part13.Main"   # Ctrl+C 退出

# 进 sbt shell 连续跑：
sbt
sbt> runMain ...
```

🎉 **Happy Hacking with Scala FP!**
