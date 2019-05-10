import com.typesafe.sbt.SbtGit.git
import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.Versions

enablePlugins(JavaAppPackaging)
enablePlugins(TutPlugin)

name := "anti-xml"

scalaVersion := "2.12.8"

organization := "com.al333z"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

description := "anti-xml"

licenses := Seq(("BSD", new URL("https://github.com/arktekk/anti-xml/blob/master/LICENSE.rst")))

homepage := Some(url("http://github.com/AL333Z/anti-xml"))

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core" % "4.5.1" % Test,
  "org.specs2" %% "specs2-scalacheck" % "4.5.1" % Test,
  "org.specs2" %% "specs2-matcher-extra" % "4.5.1" % Test,
  "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
  "com.github.julien-truffaut" %% "monocle-core" % "1.5.1-cats",
  "com.github.julien-truffaut" %% "monocle-law" % "1.5.1-cats" % Test,
  "org.typelevel" %% "cats-core" % "2.0.0-M1"
)

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ ⇒ false }

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishConfiguration := publishConfiguration.value.withOverwrite(true)
publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)

scmInfo := Some(ScmInfo(
  url("http://github.com/AL333Z/anti-xml"),
  "scm:git:git://github.com/AL333Z/anti-xml.git"
))

homepage := scmInfo.value.map(_.browseUrl)

developers := List(
  Developer(id = "djspiewak", name = "Daniel Spiewak", email = null, url = url("http://twitter.com/djspiewak"))
)

val VersionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

def setReleaseVersionCustom(): ReleaseStep = {
  def setVersionOnly(selectVersion: Versions => String): ReleaseStep = { st: State =>
    val vs = st.get(ReleaseKeys.versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val selected = selectVersion(vs)
    st.log.info("Setting version to '%s'." format selected)
    val useGlobal = Project.extract(st).get(releaseUseGlobalVersion)

    reapply(Seq(
      if (useGlobal) version in ThisBuild := selected
      else version := selected
    ), st)
  }

  setVersionOnly(_._1)
}

git.useGitDescribe := true
git.baseVersion := "0.0.0"
git.gitTagToVersionNumber := {
  case VersionRegex(v, "") => Some(v)
  case VersionRegex(v, s) => Some(s"$v-$s")
  case _ => None
}

releaseVersionBump := sbtrelease.Version.Bump.Bugfix

releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  setReleaseVersionCustom(),
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _)),
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
  pushChanges
)

tutTargetDirectory := baseDirectory.value
