lazy val `scala-dzi` = project.in(file("."))

name := "scala-dzi"
scalaVersion := "2.13.7"
scalacOptions += "--deprecation"
scalacOptions += "-target:jvm-1.8"

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

ThisBuild / organization := "io.github.olegych"
ThisBuild / organizationName := "OlegYch"
ThisBuild / organizationHomepage := Some(url("https://github.com/OlegYch"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/OlegYch/scala-dzi"),
    "scm:git@github.com:OlegYch/scala-dzi.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "OlegYch",
    name  = "Aleh Aleshka",
    email = "olegych@tut.by",
    url   = url("https://github.com/OlegYch")
  )
)

ThisBuild / description := "Deepzoom image creation library for scala."
ThisBuild / licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))
ThisBuild / homepage := Some(url("https://github.com/OlegYch/scala-dzi"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeProfileName := "OlegYch"

import ReleaseTransformations._
ThisBuild / releaseCrossBuild := true
ThisBuild / releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
