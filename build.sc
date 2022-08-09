import mill._
import scalalib._

object core extends SbtModule {
  def scalaVersion = "3.1.1"
}

object common extends SbtModule {
  def scalaVersion = "3.1.1"
  def moduleDeps   = Seq(core)
  def ivyDeps = Agg(
    ivy"org.jgrapht:jgrapht-core:1.5.1",
    ivy"org.jgrapht:jgrapht-opt:1.5.1",
    ivy"org.scalanlp::breeze:2.0.1-RC1",
    ivy"com.outr::scribe:3.10.2"
  )
}

object forsyde extends SbtModule {
  def scalaVersion = "3.1.1"
  def moduleDeps   = Seq(common, core)
  def ivyDeps = Agg(
    ivy"io.github.forsyde:forsyde-io-java-core:0.5.12",
    ivy"org.jgrapht:jgrapht-core:1.5.1",
    ivy"org.jgrapht:jgrapht-opt:1.5.1",
    ivy"org.typelevel::spire:0.18.0"
  )
}

object minizinc extends SbtModule {
  def scalaVersion = "3.1.1"
  def moduleDeps   = Seq(common, core, forsyde)
  def ivyDeps = Agg(
    ivy"org.apache.commons:commons-math3:3.6.1",
    ivy"com.outr::scribe:3.5.5",
    ivy"com.lihaoyi::upickle:1.4.0",
    ivy"org.jgrapht:jgrapht-core:1.5.1",
    ivy"org.jgrapht:jgrapht-opt:1.5.1",
    ivy"org.scalanlp::breeze:2.0.1-RC1"
  )
}

object choco extends SbtModule {
  def scalaVersion = "3.1.1"
  def moduleDeps   = Seq(common, core, forsyde)
  def ivyDeps = Agg(
    ivy"org.choco-solver:choco-solver:4.10.9-SNAPSHOT",
    ivy"org.jgrapht:jgrapht-core:1.5.1",
    ivy"org.jgrapht:jgrapht-opt:1.5.1",
    ivy"com.outr::scribe:3.5.5"
  )
  def repositoriesTask = T.task {
    super.repositoriesTask() ++ Seq(
      MavenRepository("~/.m2")
    )
  }

}

object cli extends SbtModule {
  def scalaVersion = "3.1.1"
  def moduleDeps   = Seq(common, core, forsyde, minizinc, choco)
  def ivyDeps = Agg(
    ivy"info.picocli:picocli:4.2.0",
    ivy"info.picocli:picocli-codegen:4.2.0:provided",
    ivy"com.github.scopt::scopt:4.0.1",
    ivy"com.outr::scribe:3.10.2"
  )
  def mainClass = Some("idesyde.IDeSyDeStandalone")
}
