ThisBuild / organization := "io.github.forsyde"
ThisBuild / version := "0.3.1"
ThisBuild / scalaVersion := "3.1.3"
ThisBuild / maintainer := "jordao@kth.se"

lazy val forsydeIoVersion = "0.5.15"
lazy val jgraphtVersion   = "1.5.1"
lazy val scribeVersion    = "3.10.2"

lazy val root = project
  .in(file("."))
  .aggregate(common, cli, choco, forsyde, minizinc)

lazy val core = (project in file("core"))

lazy val common = (project in file("common"))
  .dependsOn(core)
  .settings(
    libraryDependencies ++= Seq(
      "org.jgrapht"   % "jgrapht-core" % jgraphtVersion,
      "org.jgrapht"   % "jgrapht-opt"  % jgraphtVersion,
      "org.scalanlp" %% "breeze"       % "2.0.1-RC1",
      "com.outr"     %% "scribe"       % scribeVersion
    )
  )

lazy val forsyde = (project in file("forsyde"))
  .dependsOn(core)
  .dependsOn(common)
  .settings(
    libraryDependencies ++= Seq(
      "io.github.forsyde" % "forsyde-io-java-core" % forsydeIoVersion,
      "org.jgrapht"       % "jgrapht-core"         % jgraphtVersion,
      "org.jgrapht"       % "jgrapht-opt"          % jgraphtVersion,
      "org.typelevel"    %% "spire"                % "0.18.0"
    )
  )

lazy val minizinc = (project in file("minizinc"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(forsyde)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-math3" % "3.6.1",
      "com.outr"          %% "scribe"        % scribeVersion,
      "com.lihaoyi"       %% "upickle"       % "1.4.0",
      "org.jgrapht"        % "jgrapht-core"  % jgraphtVersion,
      "org.jgrapht"        % "jgrapht-opt"   % jgraphtVersion,
      "org.scalanlp"      %% "breeze"        % "2.0.1-RC1",
      "com.lihaoyi"       %% "upickle"       % "1.4.0"
    )
  )

lazy val choco = (project in file("choco"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(forsyde)
  .settings(
    libraryDependencies ++= Seq(
      "com.novocode"     % "junit-interface" % "0.11" % "test",
      "org.choco-solver" % "choco-solver"    % "4.10.9",
      "org.jgrapht"      % "jgrapht-core"    % jgraphtVersion,
      "org.jgrapht"      % "jgrapht-opt"     % jgraphtVersion,
      "com.outr"        %% "scribe"          % scribeVersion
    )
  )

lazy val cli = (project in file("cli"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(choco)
  .dependsOn(forsyde)
  .dependsOn(minizinc)
  // .enablePlugins(ScalaNativePlugin)
  .enablePlugins(UniversalPlugin, JlinkPlugin, JavaAppPackaging, GraalVMNativeImagePlugin)
  .settings(
    Compile / mainClass := Some("idesyde.IDeSyDeStandalone"),
    libraryDependencies ++= Seq(
      // "info.picocli"      % "picocli"         % "4.2.0",
      // "info.picocli"      % "picocli-codegen" % "4.2.0" % "provided",
      "com.github.scopt" %% "scopt"  % "4.0.1",
      "com.outr"         %% "scribe" % scribeVersion
    ),
    Test / parallelExecution := false
  )

lazy val tests = (project in file("tests"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(choco)
  .dependsOn(forsyde)
  .dependsOn(minizinc)
  .dependsOn(cli)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest"    %% "scalatest"                % "3.2.12" % "test",
      "org.scalatest"    %% "scalatest-funsuite"       % "3.2.12" % "test",
      "io.github.forsyde" % "forsyde-io-java-core"     % forsydeIoVersion,
      "io.github.forsyde" % "forsyde-io-java-amalthea" % forsydeIoVersion,
      "io.github.forsyde" % "forsyde-io-java-sdf3"     % forsydeIoVersion,
      "io.github.forsyde" % "forsyde-io-java-graphviz" % forsydeIoVersion
    )
  )

// ThisBuild / resolvers += Resolver.mavenLocal

// TODO: figure out what is
ThisBuild / assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}

// /Compile / resourceDirectory := baseDirectory.value / "resources"
