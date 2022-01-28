resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  "io.github.forsyde"  % "forsyde-io-java-core" % "0.5.0",
  "org.apache.commons" % "commons-math3"        % "3.6.1"
)
libraryDependencies += "com.outr" %% "scribe" % "3.5.5"
libraryDependencies += "com.lihaoyi" %% "upickle" % "1.4.0"
libraryDependencies += "org.jgrapht" % "jgrapht-unimi-dsi" % "1.5.1"
libraryDependencies += "org.choco-solver" % "choco-solver" % "4.10.8"
