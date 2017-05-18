lazy val commonSettings = Seq(
  organization := "com.bloomreach",
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.8",
  crossPaths := false,
  exportJars := true,
  isSnapshot := true,
  resolvers += Resolver.mavenLocal,
  publishMavenStyle := true,
  publishTo := Some(Resolver.mavenLocal)
)

lazy val core = (project in file("config-management-core"))
  .settings(
    commonSettings
  )

lazy val system = (project in file("."))
  .aggregate(core)
  .settings(
    commonSettings,
    name := "config-management-system"
  )
