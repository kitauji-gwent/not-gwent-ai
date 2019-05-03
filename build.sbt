name := "not-gwent-ai"

version := "0.1"

scalaVersion := "2.12.8"

scalacOptions := Seq(
  "-deprecation",
  "-encoding", "utf-8",
  "-explaintypes",
  "-feature",
  "-unchecked",
  //  "-Xlog-implicits",
  "-Xfatal-warnings",
  "-Xfuture",
  "-Xlint:inaccessible",
  "-Xlint:infer-any",
  "-Xlint:missing-interpolator",
  "-Xlint:option-implicit",
  "-Xlint:type-parameter-shadow",
  "-Xlint:unsound-match",
  "-Ypartial-unification",
  "-Ywarn-dead-code",
  "-Ywarn-inaccessible",
  "-Ywarn-infer-any",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused:imports",
  "-Ywarn-unused:privates",
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros", // Allow macro definition (besides implementation and application)
  "-language:higherKinds", // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
)

val commonsVersion = "1.34.17"

libraryDependencies += "com.avsystem.commons" %% "commons-core" % commonsVersion
libraryDependencies += "org.typelevel" %% "cats-effect" % "1.2.0"
libraryDependencies += "co.fs2" %% "fs2-core" % "1.0.4"
libraryDependencies += "org.java-websocket" % "Java-WebSocket" % "1.4.0"
libraryDependencies += "org.tpolecat" %% "atto-core" % "0.6.5"
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.5"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.0")
addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.0")
