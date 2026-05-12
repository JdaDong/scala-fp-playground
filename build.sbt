ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.7"

lazy val root = (project in file("."))
  .settings(
    name := "scala-fp-playground",
    libraryDependencies ++= Seq(
      // 函数式基础
      "org.typelevel" %% "cats-core"            % "2.10.0",
      "org.typelevel" %% "cats-free"            % "2.10.0",
      "org.typelevel" %% "cats-mtl"             % "1.4.0",
      "org.typelevel" %% "cats-effect"          % "3.5.4",

      // 流处理
      "co.fs2"        %% "fs2-core"             % "3.10.2",
      "co.fs2"        %% "fs2-io"               % "3.10.2",

      // http4s（Web 服务端）
      "org.http4s"    %% "http4s-ember-server"  % "0.23.27",
      "org.http4s"    %% "http4s-ember-client"  % "0.23.27",
      "org.http4s"    %% "http4s-dsl"           % "0.23.27",
      "org.http4s"    %% "http4s-circe"         % "0.23.27",

      // circe（JSON）
      "io.circe"      %% "circe-core"           % "0.14.9",
      "io.circe"      %% "circe-generic"        % "0.14.9",
      "io.circe"      %% "circe-parser"         % "0.14.9",

      // doobie（函数式 JDBC）
      "org.tpolecat"  %% "doobie-core"          % "1.0.0-RC5",
      "org.tpolecat"  %% "doobie-hikari"        % "1.0.0-RC5",
      "org.tpolecat"  %% "doobie-h2"            % "1.0.0-RC5",

      // 日志
      "org.typelevel"  %% "log4cats-slf4j"      % "2.7.0",
      "ch.qos.logback" %  "logback-classic"     % "1.5.6"
    )
  )

// ============================================================
// 自定义 task：一键串跑 part01 ~ part12 所有非阻塞 demo
//   用法：sbt runAllDemos
//   实现：每个 demo 通过 java 命令派生独立 JVM 运行，
//        效果与 `sbt "runMain ..."` 一致（避免 sbt 内嵌 runner 的栈深度限制）
//   跳过：part06.Scene04（耗时较长）/ part10.Scene04（长阻塞）/ part13.Main（永久 HTTP 服务）
// ============================================================
lazy val runAllDemos = taskKey[Unit]("一键串跑全部非阻塞 demo（part01 ~ part12）")

runAllDemos := {
  val log = streams.value.log
  val cp  = (Compile / fullClasspath).value.files

  val cpStr = cp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
  val javaBin = {
    val javaHome = System.getProperty("java.home")
    new java.io.File(javaHome, "bin/java").getAbsolutePath
  }

  val demos: Seq[String] = Seq(
    "demo.part01.demo01",
    "demo.part02.CurryingExamples",
    "demo.part03.AdvancedScalaFeatures",
    "demo.part04.ScenesRunner",
    "demo.part05.Scene01_ForComprehension",
    "demo.part05.Scene02_CaseClassPatternMatch",
    "demo.part05.Scene03_CatsValidated",
    "demo.part06.Scene01_FutureVsIO",
    "demo.part06.Scene02_Cancellation",
    "demo.part06.Scene03_ResourceSafety",
    "demo.part07.Scene01_WhyTypeClass",
    "demo.part07.Scene02_StandardPattern",
    "demo.part07.Scene03_Derivation",
    "demo.part07.Scene04_CatsTypeClasses",
    "demo.part07.Scene05_PluggableETL",
    "demo.part08.Scene01_FromConcreteToTagless",
    "demo.part08.Scene02_MultipleInterpreters",
    "demo.part08.Scene03_TaglessIndustrial",
    "demo.part09.Scene01_FreeMonadIntro",
    "demo.part09.Scene02_FreeAdvanced",
    "demo.part10.Scene01_FS2Intro",
    "demo.part10.Scene02_FS2Backpressure",
    "demo.part10.Scene03_FS2Pipeline",
    "demo.part11.Scene01_HKTBasics",
    "demo.part11.Scene02_Variance",
    "demo.part11.Scene03_HKTAdvanced",
    "demo.part12.Scene01_MTPain",
    "demo.part12.Scene02_CatsMtlIntro",
    "demo.part12.Scene03_TwoInterpreters"
  )

  val results = scala.collection.mutable.ListBuffer.empty[(String, Boolean, Long)]
  val total   = demos.size

  log.info("=" * 70)
  log.info(s"🚀 开始串跑 $total 个 demo（已跳过 3 个阻塞型入口）")
  log.info("=" * 70)

  demos.zipWithIndex.foreach { case (cls, i) =>
    log.info(s"\n>>> [${i + 1}/$total] $cls")
    val t0 = System.currentTimeMillis()
    val pb = new java.lang.ProcessBuilder(javaBin, "-cp", cpStr, cls).inheritIO()
    val proc = pb.start()
    val exitCode = proc.waitFor()
    val ms = System.currentTimeMillis() - t0
    val ok = (exitCode == 0)
    results += ((cls, ok, ms))
    val mark = if (ok) "✓" else s"✗ 失败 (exit=$exitCode)"
    log.info(s"<<< [${i + 1}/$total] $cls  耗时 ${ms} ms  $mark")
  }

  log.info("\n" + "=" * 70)
  log.info("📊 汇总")
  log.info("=" * 70)
  val passed = results.count(_._2)
  val failed = results.filterNot(_._2).map(_._1)
  log.info(f"  通过：$passed%2d / $total")
  if (failed.nonEmpty) {
    log.warn(s"  失败：${failed.size}")
    failed.foreach(c => log.warn(s"    - $c"))
  }
  log.info(s"  总耗时：${results.map(_._3).sum / 1000} 秒")
  log.info("=" * 70)
}