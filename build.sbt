import AssemblyKeys._

assemblySettings

jarName in assembly := "Business-Finder-Uber.jar"

name := """business-finder"""

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  // Change this to another test framework if you prefer
  "org.scalatest" %% "scalatest" % "2.1.6" % "test",
  // Akka
  "com.typesafe.akka" %% "akka-actor" % "2.3.5",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.5",
  "com.typesafe" % "config" % "1.0.0",
  "org.jsoup" % "jsoup" % "1.7.2",
  "com.github.tototoshi" %% "scala-csv" % "1.0.0",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.5"  
)



