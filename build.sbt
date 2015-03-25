name := """coucdb-views"""

version := "1.0"

scalaVersion := "2.11.5"

// Change this to another test framework if you prefer
libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"

libraryDependencies ++= Seq(
	 "com.twitter" % "finagle-http_2.11" % "6.24.0",
	 "com.typesafe" % "config" % "1.2.1",
	 "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.5.1"
)
