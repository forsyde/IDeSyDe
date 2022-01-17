/* lazy val root = project
  .in(file("."))
  .enablePlugins(NativeImagePlugin)
  .settings(
    name := "IDeSyDe",
    description := "",
    version := "0.2.3",
    scalaVersion := "3.1.0",
    Compile / mainClass := Some("idesyde.IDeSyDeStandalone")
  )
 */
// resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  "io.github.forsyde" % "forsyde-io-java-core" % "0.4.1",
  "org.apache.commons" % "commons-math3" % "3.6.1"
)
libraryDependencies += "com.outr" %% "scribe" % "3.5.5"
// libraryDependencies += "com.google.ortools" % "ortools-java" % "9.0.9048"
// libraryDependencies += "com.google.ortools" % "ortools-linux-x86-64" % "9.0.9048"
libraryDependencies += "com.lihaoyi" %% "upickle" % "1.4.0"
libraryDependencies += "org.jgrapht" % "jgrapht-unimi-dsi" % "1.5.1"
libraryDependencies += "org.choco-solver" %% "choco-solver" % "4.10.8"
// libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.14.1"
// libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.14.1"
// libraryDependencies += "org.apache.logging.log4j" %% "log4j-api-scala" % "11.0"
// https://mvnrepository.com/artifact/org.slf4j/slf4j-api
// https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
// libraryDependencies += ("org.typelevel" %% "cats-core" % "2.3.0")
//   .withCrossVersion(CrossVersion.for3Use2_13)

// segments to be able to use python
// libraryDependencies += ("me.shadaj" %% "scalapy-core" % "0.5.0").cross(
//   CrossVersion.for3Use2_13
// )

// fork := true

// import scala.sys.process._
// lazy val pythonLdFlags = {
//   val withoutEmbed = "python3-config --ldflags".!!
//   if (withoutEmbed.contains("-lpython")) {
//     withoutEmbed.split(' ').map(_.trim).filter(_.nonEmpty).toSeq
//   } else {
//     val withEmbed = "python3-config --ldflags --embed".!!
//     withEmbed.split(' ').map(_.trim).filter(_.nonEmpty).toSeq
//   }
// }

// lazy val pythonLibsDir = {
//   pythonLdFlags.find(_.startsWith("-L")).get.drop("-L".length)
// }

// javaOptions += s"-Djna.library.path=$pythonLibsDir"


// /Compile / resourceDirectory := baseDirectory.value / "resources"
