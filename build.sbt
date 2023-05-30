maintainer := "jordao@kth.se"
organization := "io.forsyde.github"

ThisBuild / scalaVersion := "3.2.2"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := Some(Opts.resolver.sonatypeStaging)

lazy val forsydeIoVersion              = "0.6.4"
lazy val jgraphtVersion                = "1.5.1"
lazy val scribeVersion                 = "3.10.2"
lazy val scalaGraphVersion             = "1.13.5"
lazy val scalaParserCombinatorsVersion = "2.2.0"
lazy val spireVersion                  = "0.18.0"
lazy val upickleVersion                = "3.0.0"
lazy val chocoSolverVersion            = "4.10.10"
lazy val osLibVersion                  = "0.9.1"
lazy val scalaYamlVersion              = "0.0.6"
lazy val scoptVersion                  = "4.1.0"
lazy val scalaJsonSchemaVersion        = "0.7.8"

lazy val imodulesTarget = file("imodules")
lazy val emodulesTarget = file("emodules")

lazy val publishModules = taskKey[File]("Copy and return modules")

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .enablePlugins(SitePreviewPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .settings(
    ScalaUnidoc / siteSubdirName := "api",
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName),
    paradoxProperties ++= Map(
      "scaladoc.base_url" -> "IDeSyDe/api",
      "github.base_url"   -> "https://github.com/forsyde/IDeSyDe"
    ),
    paradoxRoots := List("index.html")
  )
  .aggregate(common, choco, forsyde, minizinc, matlab, devicetree, blueprints)

lazy val core = (project in file("scala-core")).settings(
  name := "idesyde-scala-core",
  libraryDependencies ++= Seq("com.lihaoyi" %%% "upickle" % upickleVersion)
)

lazy val blueprints = (project in file("scala-blueprints"))
  .dependsOn(core)
  .settings(
    name := "idesyde-scala-blueprints",
    libraryDependencies ++= Seq(
      "com.lihaoyi"      %%% "os-lib" % osLibVersion,
      "com.github.scopt" %%% "scopt"  % scoptVersion
    ),
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0")
    )
  )

lazy val common = (project in file("scala-common"))
  .dependsOn(core)
  .dependsOn(blueprints)
  // .enablePlugins(ScalaNativePlugin)
  .enablePlugins(UniversalPlugin, JavaAppPackaging, JlinkPlugin)
  .enablePlugins(JDKPackagerPlugin)
  // .enablePlugins(GraalVMNativeImagePlugin)
  .settings(
    name := "idesyde-scala-common",
    libraryDependencies ++= Seq(
      ("org.scala-graph" %% "graph-core" % scalaGraphVersion).cross(CrossVersion.for3Use2_13),
      "org.typelevel"   %%% "spire"      % spireVersion
    ),
    mainClass := Some("idesyde.common.CommonIdentificationModule"),
    publishModules := {
      IO.createDirectory(imodulesTarget)
      val jar    = assembly.value
      val target = imodulesTarget / (projectInfo.value.nameFormal + ".jar")
      IO.copyFile(jar, target)
      target
    },
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0")
    ),
    jlinkIgnoreMissingDependency := JlinkIgnore.byPackagePrefix(
      "scala.quoted"                -> "scala",
      "scalax.collection.generator" -> "org.scalacheck"
    )
  )

lazy val forsyde = (project in file("scala-bridge-forsyde-io"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(blueprints)
  .enablePlugins(UniversalPlugin, JavaAppPackaging, JlinkPlugin)
  .enablePlugins(JDKPackagerPlugin)
  // .enablePlugins(GraalVMNativeImagePlugin)
  .settings(
    name := "idesyde-scala-bridge-forsyde-io",
    libraryDependencies ++= Seq(
      "io.github.forsyde"  % "forsyde-io-java-core" % forsydeIoVersion,
      "io.github.forsyde"  % "forsyde-io-java-sdf3" % forsydeIoVersion,
      "org.apache.commons" % "commons-lang3"        % "3.12.0"
      // "io.github.forsyde"        % "forsyde-io-java-amalthea"          % forsydeIoVersion,
      // "org.eclipse.app4mc"       % "org.eclipse.app4mc.amalthea.model" % "2.2.0",
      // "org.eclipse.birt.runtime" % "org.eclipse.emf.common"            % "2.12.0.v20160420-0247",
      // "org.eclipse.birt.runtime" % "org.eclipse.emf.ecore"             % "2.12.0.v20160420-0247"
    ),
    mainClass := Some("idesyde.forsydeio.ForSyDeIdentificationModule"),
    publishModules := {
      IO.createDirectory(imodulesTarget)
      val jar    = assembly.value
      val target = imodulesTarget / (projectInfo.value.nameFormal + ".jar")
      IO.copyFile(jar, target)
      target
    },
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
      "EPL2" -> url("https://www.eclipse.org/legal/epl-2.0/")
    ),
    jlinkModulePath := {
      val paths = (jlinkBuildImage / fullClasspath).value
      paths
        .filter(f => {
          f.get(moduleID.key)
            .exists(mID =>
              mID.name.contains("jheaps") ||
                mID.name.contains("antlr4") ||
                mID.name.contains("automaton") ||
                mID.name.contains("xchart") ||
                mID.name == "commons-lang3" ||
                mID.name.contains("trove4j")
            )
          // f.get(moduleID.key).exists(mID => mID.name.contains("amalthea")) ||
          // f.get(moduleID.key).exists(mID => mID.name.contains("emf")) ||
          // f.get(moduleID.key).exists(mID => mID.name.contains("lang3"))
        })
        .map(_.data)
    },
    jlinkIgnoreMissingDependency := JlinkIgnore.byPackagePrefix(
      "scala.quoted"                          -> "scala",
      "scalax.collection.generator"           -> "org.scalacheck",
      "org.glassfish.jaxb.runtime.v2.runtime" -> "com.sun.xml",
      "org.glassfish.jaxb.runtime.v2.runtime" -> "org.jvnet",
      "org.antlr.runtime"                     -> "org.antlr.stringtemplate"
    )
  )

lazy val minizinc = (project in file("scala-minizinc"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(blueprints)
  .settings(
    name := "idesyde-scala-minizinc",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % upickleVersion
    ),
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
      "EPL2" -> url("https://www.eclipse.org/legal/epl-2.0/")
    )
  )

lazy val choco = (project in file("scala-choco"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(forsyde)
  .dependsOn(blueprints)
  .enablePlugins(UniversalPlugin, JavaAppPackaging, JlinkPlugin)
  .enablePlugins(JDKPackagerPlugin)
  // .enablePlugins(GraalVMNativeImagePlugin)
  .settings(
    name := "idesyde-scala-choco",
    libraryDependencies ++= Seq(
      "com.novocode"     % "junit-interface" % "0.11" % "test",
      "org.choco-solver" % "choco-solver"    % chocoSolverVersion,
      "org.jgrapht"      % "jgrapht-core"    % jgraphtVersion
    ),
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
      "EPL2" -> url("https://www.eclipse.org/legal/epl-2.0/")
    ),
    Compile / mainClass := Some("idesyde.choco.ChocoExplorationModule"),
    publishModules := {
      IO.createDirectory(emodulesTarget)
      val jar    = assembly.value
      val target = emodulesTarget / (projectInfo.value.nameFormal + ".jar")
      IO.copyFile(jar, target)
      target
    },
    jlinkModulePath := {
      val paths = (jlinkBuildImage / fullClasspath).value
      paths
        .filter(f => {
          f.get(moduleID.key).exists(mID => mID.name.contains("jheaps")) ||
          // f.get(moduleID.key).exists(mID => mID.name.contains("fastutil")) ||
          // f.get(moduleID.key).exists(mID => mID.name.contains("commons-text")) ||
          f.get(moduleID.key).exists(mID => mID.name.contains("antlr4")) ||
          f.get(moduleID.key).exists(mID => mID.name.contains("automaton")) ||
          f.get(moduleID.key).exists(mID => mID.name.contains("xchart")) ||
          f.get(moduleID.key).exists(mID => mID.name.contains("trove4j"))
        })
        .map(_.data)
    },
    graalVMNativeImageOptions := Seq("--no-fallback", "-H:+ReportExceptionStackTraces"),
    jlinkIgnoreMissingDependency := JlinkIgnore.byPackagePrefix(
      "scala.quoted"                          -> "scala",
      "scalax.collection.generator"           -> "org.scalacheck",
      "org.glassfish.jaxb.runtime.v2.runtime" -> "com.sun.xml",
      "org.glassfish.jaxb.runtime.v2.runtime" -> "org.jvnet",
      "org.antlr.runtime"                     -> "org.antlr.stringtemplate",
      "org.knowm.xchart"                      -> "org.apache.pdfbox",
      "org.knowm.xchart"                      -> "de.rototor",
      "org.knowm.xchart"                      -> "de.erichseifert",
      "org.knowm.xchart"                      -> "com.madgag"
    )
  )

lazy val matlab = (project in file("scala-bridge-matlab"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(blueprints)
  .settings(
    name := "idesyde-scala-bridge-matlab",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %%% "scala-parser-combinators" % scalaParserCombinatorsVersion,
      "com.lihaoyi"             %% "os-lib"                   % osLibVersion,
      "org.virtuslab"           %% "scala-yaml"               % scalaYamlVersion
    ),
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
      "EPL2" -> url("https://www.eclipse.org/legal/epl-2.0/")
    ),
    Compile / mainClass := Some("idesyde.matlab.SimulinkMatlabIdentificationModule"),
    publishModules := {
      IO.createDirectory(imodulesTarget)
      val jar    = assembly.value
      val target = imodulesTarget / (projectInfo.value.nameFormal + ".jar")
      IO.copyFile(jar, target)
      target
    }
  )

lazy val devicetree = (project in file("scala-bridge-device-tree"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(blueprints)
  .enablePlugins(UniversalPlugin, JavaAppPackaging, JlinkPlugin)
  .enablePlugins(JDKPackagerPlugin)
  .settings(
    name := "idesyde-scala-bridge-devicetree",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %%% "scala-parser-combinators" % scalaParserCombinatorsVersion,
      "com.lihaoyi"             %% "os-lib"                   % osLibVersion,
      "org.virtuslab"           %% "scala-yaml"               % scalaYamlVersion
    ),
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
      "EPL2" -> url("https://www.eclipse.org/legal/epl-2.0/")
    ),
    Compile / mainClass := Some("idesyde.devicetree.DeviceTreeIdentificationModule"),
    publishModules := {
      IO.createDirectory(imodulesTarget)
      val jar    = assembly.value
      val target = imodulesTarget / (projectInfo.value.nameFormal + ".jar")
      IO.copyFile(jar, target)
      target
    }
  )

// lazy val cli = (project in file("scala-cli"))
//   .dependsOn(core)
//   .dependsOn(common)
//   .dependsOn(choco)
//   .dependsOn(forsyde)
//   .dependsOn(minizinc)
//   .dependsOn(matlab)
//   .dependsOn(devicetree)
//   // .enablePlugins(ScalaNativePlugin)
//   .enablePlugins(UniversalPlugin, JavaAppPackaging, JlinkPlugin)
//   .enablePlugins(JDKPackagerPlugin)
//   .enablePlugins(GraalVMNativeImagePlugin)
//   .settings(
//     publishArtifact := false,
//     licenses := Seq(
//       "MIT"  -> url("https://opensource.org/license/mit/"),
//       "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
//       "EPL2" -> url("https://www.eclipse.org/legal/epl-2.0/")
//     ),
//     Compile / mainClass := Some("idesyde.IDeSyDeStandalone"),
//     libraryDependencies ++= Seq(
//       "com.github.scopt" %% "scopt"  % scoptVersion,
//       "com.lihaoyi"      %% "os-lib" % osLibVersion
//       // "com.outr"         %% "scribe"      % scribeVersion,
//       // "com.outr"         %% "scribe-file" % scribeVersion
//     ),
//     // taken and adapted from https://www.scala-sbt.org/sbt-native-packager/archetypes/jlink_plugin.html
//     jlinkModulePath := {
//       val paths = (jlinkBuildImage / fullClasspath).value
//       paths
//         .filter(f => {
//           f.get(moduleID.key).exists(mID => mID.name.contains("jheaps")) ||
//           // f.get(moduleID.key).exists(mID => mID.name.contains("fastutil")) ||
//           // f.get(moduleID.key).exists(mID => mID.name.contains("commons-text")) ||
//           f.get(moduleID.key).exists(mID => mID.name.contains("antlr4")) ||
//           f.get(moduleID.key).exists(mID => mID.name.contains("automaton")) ||
//           f.get(moduleID.key).exists(mID => mID.name.contains("xchart")) ||
//           f.get(moduleID.key).exists(mID => mID.name.contains("trove4j"))
//         })
//         .map(_.data)
//     },
//     graalVMNativeImageOptions := Seq("--no-fallback", "-H:+ReportExceptionStackTraces"),
//     jlinkIgnoreMissingDependency := JlinkIgnore.byPackagePrefix(
//       "scala.quoted"                          -> "scala",
//       "scalax.collection.generator"           -> "org.scalacheck",
//       "org.glassfish.jaxb.runtime.v2.runtime" -> "com.sun.xml",
//       "org.glassfish.jaxb.runtime.v2.runtime" -> "org.jvnet",
//       "org.antlr.runtime"                     -> "org.antlr.stringtemplate",
//       "org.knowm.xchart"                      -> "org.apache.pdfbox",
//       "org.knowm.xchart"                      -> "de.rototor",
//       "org.knowm.xchart"                      -> "de.erichseifert",
//       "org.knowm.xchart"                      -> "com.madgag"
//     )
//   )

// TODO: figure out what is
ThisBuild / assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}

// /Compile / resourceDirectory := root.base / "resources"
lazy val publishDocumentation =
  taskKey[Unit]("Copy the generated documentation to the correct folder")
publishDocumentation := IO.copyDirectory(
  (root / makeSite).value,
  new java.io.File("docs"),
  true,
  false,
  false
)
