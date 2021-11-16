lazy val `scala-dzi` = project.in(file("."))

name := "scala-dzi"
organization := "org.olegych"
version := "0.1-SNAPSHOT"
scalaVersion := "2.13.7"
scalacOptions += "--deprecation"

libraryDependencies += "com.google.guava" % "guava" % "31.0.1-jre"
libraryDependencies += "com.alexdupre" % "pngj" % "2.1.2.1"
libraryDependencies += "com.alexdupre" %% "bmp4s" % "0.5.1"
libraryDependencies += "gov.nist.isg" % "pyramidio" % "1.1.0" % Test
libraryDependencies += "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4"
libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core" % "4.13.0" % Test,
  "org.specs2" %% "specs2-junit" % "4.13.0" % Test,
  "org.specs2" %% "specs2-matcher-extra" % "4.13.0" % Test
)

Test / javaOptions ++= List("-Xmx10g", "-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:+UseJVMCICompiler")
Test / fork := true

crossScalaVersions := Seq(scalaVersion.value)
ThisBuild / missinglinkExcludedDependencies ++= Seq(
  moduleFilter("org.slf4j"),
  moduleFilter("ch.qos.logback"),
)
ThisBuild / missinglinkIgnoreSourcePackages ++= Seq(
  IgnoredPackage("nu.validator.htmlparser.extra"),
  IgnoredPackage("javax.xml.bind") //tries to use java modules
)
ThisBuild / missinglinkIgnoreDestinationPackages ++= Seq(
  IgnoredPackage("android"),
  IgnoredPackage("dalvik"),
  IgnoredPackage("org.apache.log4j"),
  IgnoredPackage("org.conscrypt"),
  IgnoredPackage("javax.xml"),
  IgnoredPackage("org.bouncycastle"),
)
cachedCiTestFull := {
  val _ = cachedCiTestFull.value
  val __ = (Compile / missinglinkCheck).value
}
