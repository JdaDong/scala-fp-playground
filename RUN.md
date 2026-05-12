# Scala 项目运行手册

> 本项目包含 **part01 ~ part13** 共 13 个学习模块、**41 个可独立运行的入口**。
> 本文档列出所有入口的运行命令 + 使用技巧。

---

## 📌 通用规则

```bash
# 1. 进入项目根目录
cd /Users/jiangdadong/CodeBuddy/scala-fp-playground

# 2. 用 sbt 运行任意一个入口
sbt "runMain <完整类路径>"

# 3. 退出阻塞型程序（如 part13 服务）：Ctrl + C
```

---

## 📚 全部可运行入口（按 part 排列）

### part01 —— Scala 基础语法

```bash
sbt "runMain demo.part01.demo01"
```

### part02 —— 柯里化

```bash
sbt "runMain demo.part02.CurryingExamples"
```

### part03 —— Scala 高级特性概览

```bash
sbt "runMain demo.part03.AdvancedScalaFeatures"
```

### part04 —— 偏函数 8 大应用场景

```bash
# ★ 一键跑全部 8 个场景
sbt "runMain demo.part04.ScenesRunner"

# 也可以单独跑每个场景：
sbt "runMain demo.part04.Scene01_OrderStateMachine"
sbt "runMain demo.part04.Scene02_LogPipeline"
sbt "runMain demo.part04.Scene03_HttpRouter"
sbt "runMain demo.part04.Scene04_DataCleansing"
sbt "runMain demo.part04.Scene05_ActorMessageHandling"
sbt "runMain demo.part04.Scene06_FormValidator"
sbt "runMain demo.part04.Scene07_SparkLikeProcessing"
sbt "runMain demo.part04.Scene08_FutureRecovery"
```

### part05 —— for 推导式 + case class + Validated

```bash
sbt "runMain demo.part05.Scene01_ForComprehension"
sbt "runMain demo.part05.Scene02_CaseClassPatternMatch"
sbt "runMain demo.part05.Scene03_CatsValidated"
```

### part06 —— Cats Effect（IO / 取消 / 资源）

```bash
sbt "runMain demo.part06.Scene01_FutureVsIO"
sbt "runMain demo.part06.Scene02_Cancellation"
sbt "runMain demo.part06.Scene03_ResourceSafety"
sbt "runMain demo.part06.Scene04_RateLimitedCrawler"
```

### part07 —— Type Class 模式

```bash
sbt "runMain demo.part07.Scene01_WhyTypeClass"
sbt "runMain demo.part07.Scene02_StandardPattern"
sbt "runMain demo.part07.Scene03_Derivation"
sbt "runMain demo.part07.Scene04_CatsTypeClasses"
sbt "runMain demo.part07.Scene05_PluggableETL"
```

### part08 —— Tagless Final

```bash
sbt "runMain demo.part08.Scene01_FromConcreteToTagless"
sbt "runMain demo.part08.Scene02_MultipleInterpreters"
sbt "runMain demo.part08.Scene03_TaglessIndustrial"
```

### part09 —— Free Monad

```bash
sbt "runMain demo.part09.Scene01_FreeMonadIntro"
sbt "runMain demo.part09.Scene02_FreeAdvanced"
```

### part10 —— fs2 Stream

```bash
sbt "runMain demo.part10.Scene01_FS2Intro"
sbt "runMain demo.part10.Scene02_FS2Backpressure"
sbt "runMain demo.part10.Scene03_FS2Pipeline"
sbt "runMain demo.part10.Scene04_FS2EventBus"
```

### part11 —— HKT + Variance

```bash
sbt "runMain demo.part11.Scene01_HKTBasics"
sbt "runMain demo.part11.Scene02_Variance"
sbt "runMain demo.part11.Scene03_HKTAdvanced"
```

### part12 —— cats-mtl（多 Monad transformer 组合）

```bash
sbt "runMain demo.part12.Scene01_MTPain"
sbt "runMain demo.part12.Scene02_CatsMtlIntro"
sbt "runMain demo.part12.Scene03_TwoInterpreters"
```

### part13 —— http4s + doobie + cats-mtl REST 服务 🌐

```bash
sbt "runMain demo.part13.Main"
# 服务跑在 http://localhost:8080，按 Ctrl+C 退出

# 另一终端测试：
curl http://localhost:8080/health
curl -X POST http://localhost:8080/todos \
  -H 'Content-Type: application/json' \
  -d '{"title":"Learn Scala FP"}'
curl http://localhost:8080/todos
```

### 🔧 状态管理 demo（不在 partXX 下）

```bash
sbt "runMain demo.StateManagementDemo"
```

---

## ⚡ 一键串跑所有非阻塞 demo

我在 `build.sbt` 里加了一个自定义 task：

```bash
sbt runAllDemos
```

**会自动按顺序跑完 part01 ~ part12 所有非阻塞示例（共 32 个）**，跳过：
- `part06.Scene04_RateLimitedCrawler`（耗时较长）
- `part10.Scene04_FS2EventBus`（长阻塞）
- `part13.Main`（永久阻塞 HTTP 服务）

---

## 💡 高效使用 sbt 的 3 个技巧

### 技巧 1：进入 sbt shell（避免每次启动 JVM）

```bash
sbt
# 然后在 sbt> 提示符下连续跑多个：
sbt> runMain demo.part12.Scene03_TwoInterpreters
sbt> runMain demo.part10.Scene02_FS2Backpressure
sbt> exit
```

**省去每次 5~10 秒的 JVM 启动时间**，强烈推荐。

### 技巧 2：列出所有 main

```bash
sbt
sbt> show discoveredMainClasses
```

sbt 会自动列出项目里所有 `def main` / `IOApp` / `App` 入口。

### 技巧 3：用分号串跑多条 sbt 命令

```bash
sbt ";runMain demo.part01.demo01 ;runMain demo.part02.CurryingExamples"
```

---

## ⚠️ 阻塞性入口注意事项

| 入口 | 阻塞类型 | 退出方式 |
|---|---|---|
| `part06.Scene04_RateLimitedCrawler` | 短时阻塞（约 10 秒） | 自动结束 |
| `part10.Scene04_FS2EventBus` | 长阻塞（按设计运行 30 秒） | 自动结束 |
| `part13.Main` | **永久阻塞**（HTTP 服务） | **Ctrl+C** |
| 其它所有 | 跑完即退 | — |

---

## 🎯 推荐学习顺序

**第一遍（基础）**：
- part01 → part02 → part03 → part04 (任挑 2 个) → part05.Scene01

**第二遍（核心）**：
- part06.Scene01/02/03 → part07.Scene01/02/04 → part08.Scene01/02

**第三遍（进阶）**：
- part10.Scene01 → part11.Scene01 → part09.Scene01 → part08.Scene03

**第四遍（实战）**：
- part12 → **part13.Main** ← 集大成项目

---

## 📊 入口总数统计

| 模块 | 入口数 | 类型 |
|---|---|---|
| part01 | 1 | 基础 |
| part02 | 1 | 基础 |
| part03 | 1 | 基础 |
| part04 | 9（8 个场景 + 1 个 ScenesRunner） | 偏函数 |
| part05 | 3 | for + 模式匹配 |
| part06 | 4 | Cats Effect |
| part07 | 5 | Type Class |
| part08 | 3 | Tagless Final |
| part09 | 2 | Free Monad |
| part10 | 4 | fs2 |
| part11 | 3 | HKT + Variance |
| part12 | 3 | cats-mtl |
| part13 | 1 | REST 服务 |
| 其它 | 1 | StateManagementDemo |
| **合计** | **41** | |

---

## 📂 配套文档导航

| 文档 | 主题 |
|---|---|
| `docs/scala_features_applications.md` | Scala 高级特性应用场景 |
| `docs/scala_for_pattern_summary.md` | for 推导式 + 模式匹配速查 |
| `docs/cats_effect_intro.md` | Cats Effect 入门 |
| `docs/type_class_pattern.md` | Type Class 模式 |
| `docs/scala_fp_final_pieces.md` | Tagless / Free / fs2 / HKT 总结 |
| `docs/cats_mtl_guide.md` | cats-mtl 实战指南 |
| `docs/http4s_doobie_mtl_guide.md` | 三件套 REST 服务架构 |
| **本文件** | **运行手册** |
