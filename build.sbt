ThisBuild / organization := "io.github.forsyde"
ThisBuild / version := "0.2.6"
ThisBuild / scalaVersion := "3.1.1"

lazy val root = project
  .in(file("."))
  .aggregate(common, cli, choco, forsyde, minizinc)

lazy val common = (project in file("common"))

lazy val choco = (project in file("choco"))
  .dependsOn(common)
  .dependsOn(forsyde)

lazy val forsyde = (project in file("forsyde"))
  .dependsOn(common)

lazy val minizinc = (project in file("minizinc"))
  .dependsOn(common)
  .dependsOn(forsyde)

lazy val cli = (project in file("cli"))
  .dependsOn(common)
  .dependsOn(choco)
  .dependsOn(forsyde)
  .dependsOn(minizinc)
  // .enablePlugins(ScalaNativePlugin)
  .settings(
    Compile / mainClass := Some("idesyde.IDeSyDeStandalone")
  )

lazy val tests = (project in file("tests"))
  .dependsOn(common)
  .dependsOn(choco)
  .dependsOn(forsyde)
  .dependsOn(minizinc)
  .dependsOn(cli)

ThisBuild / resolvers += Resolver.mavenLocal

// TODO: figure out what is
ThisBuild / assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}

// /Compile / resourceDirectory := baseDirectory.value / "resources"
