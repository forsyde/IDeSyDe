import mill._
import scalalib._
import scalanativelib._
import coursier.maven.MavenRepository

val globalScalaVersion       = "3.1.3"
val globalScalaNativeVersion = "0.4.5"
val globalForSyDeIOVersion   = "0.5.15"

val localRepos = Seq() // ++ Seq(
//   MavenRepository("file:///C:/Users/RodolfoJordao/.m2/repository"),
//   MavenRepository("file:///C:/Users/jorro/.m2/repository")
// )

object core extends SbtModule {
  def scalaVersion = globalScalaVersion
}

object common extends SbtModule {
  def scalaVersion = globalScalaVersion
  def moduleDeps   = Seq(core)
  def ivyDeps = Agg(
    ivy"org.jgrapht:jgrapht-core:1.5.1",
    ivy"org.jgrapht:jgrapht-opt:1.5.1",
    ivy"org.scalanlp::breeze:2.0.1-RC1",
    ivy"com.outr::scribe:3.10.2"
  )
}

object forsyde extends SbtModule {
  def scalaVersion = globalScalaVersion
  def moduleDeps   = Seq(common, core)
  def repositories = super.repositories ++ localRepos
  def ivyDeps = Agg(
    ivy"io.github.forsyde:forsyde-io-java-core:${globalForSyDeIOVersion}",
    ivy"org.jgrapht:jgrapht-core:1.5.1",
    ivy"org.jgrapht:jgrapht-opt:1.5.1",
    ivy"org.typelevel::spire:0.18.0"
  )
}

object minizinc extends SbtModule {
  def scalaVersion = globalScalaVersion
  def moduleDeps   = Seq(common, core, forsyde)
  def repositories = super.repositories ++ localRepos
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
  def scalaVersion = globalScalaVersion
  def moduleDeps   = Seq(common, core, forsyde)
  def repositories = super.repositories ++ localRepos
  def ivyDeps = Agg(
    ivy"org.choco-solver:choco-solver:4.10.9",
    ivy"org.jgrapht:jgrapht-core:1.5.1",
    ivy"org.jgrapht:jgrapht-opt:1.5.1",
    ivy"com.outr::scribe:3.5.5"
  )

}

// object `java-choco` extends MavenModule with ScalaModule {
//   def scalaVersion = globalScalaVersion
//   def moduleDeps   = Seq(common, core)
//   def ivyDeps = Agg(
//     ivy"org.choco-solver:choco-solver:4.10.8"
//   )
// }

object cli extends SbtModule {
  def scalaVersion       = globalScalaVersion
  def scalaNativeVersion = globalScalaNativeVersion
  def moduleDeps         = Seq(common, core, forsyde, minizinc, choco)
  def repositories =
    super.repositories ++ localRepos
  def ivyDeps = Agg(
    ivy"com.github.scopt::scopt:4.0.1",
    ivy"com.outr::scribe:3.10.2"
  )
  def mainClass = Some("idesyde.IDeSyDeStandalone")

  def nativeImage = T {
    os.proc("native-image", "--no-fallback", "-jar", assembly().path).call()
  }
}

object tests extends SbtModule {
  def scalaVersion = globalScalaVersion
  def moduleDeps   = Seq(common, core, forsyde, minizinc, choco)
  def repositories =
    super.repositories ++ localRepos
  def ivyDeps = Agg(
    ivy"org.scalatest::scalatest:3.2.12",
    ivy"org.scalatest::scalatest-funsuite:3.2.12",
    ivy"io.github.forsyde:forsyde-io-java-core:${globalForSyDeIOVersion}",
    ivy"io.github.forsyde:forsyde-io-java-amalthea:${globalForSyDeIOVersion}",
    ivy"io.github.forsyde:forsyde-io-java-sdf3:${globalForSyDeIOVersion}",
    ivy"io.github.forsyde:forsyde-io-java-graphviz:${globalForSyDeIOVersion}"
  )

  object test extends Tests with TestModule.ScalaTest {}

}
