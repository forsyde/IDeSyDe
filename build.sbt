lazy val root = project
  .in(file("."))
  .settings(
    name := "IDeSyDe",
    description := "",
    version := "0.2.1",
    scalaVersion := "3.0.0"
  )

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  "io.github.forsyde" % "forsyde-io-java" % "0.3.7",
  "info.picocli" % "picocli" % "4.2.0",
  "info.picocli" % "picocli-codegen" % "4.2.0" % "provided"
)
libraryDependencies += "org.apache.commons" % "commons-math3" % "3.6.1"

// segments to be able to use python
// libraryDependencies += ("me.shadaj" %% "scalapy-core" % "0.5.0").cross(
//   CrossVersion.for3Use2_13
// )

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
