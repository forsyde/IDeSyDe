ThisBuild / organization := "io.github.forsyde"
ThisBuild / version := "0.2.4"
ThisBuild / scalaVersion := "3.1.1"

lazy val root = project
  .in(file("."))
  .aggregate(common, cli, choco)

lazy val common = (project in file("common"))

lazy val choco = (project in file("choco")).dependsOn(common)

lazy val cli = (project in file("cli"))
  .dependsOn(common)
  .dependsOn(choco)
  .enablePlugins(ScalaNativePlugin)
  .settings(
    Compile / mainClass := Some("idesyde.IDeSyDeStandalone")
  )

//ThisBuild / resolvers += Resolver.mavenLocal


// TODO: figure out what is
ThisBuild / assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}

// /Compile / resourceDirectory := baseDirectory.value / "resources"
