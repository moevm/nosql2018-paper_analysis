name := "papers-server"

version := "0.1"

scalaVersion := "2.12.6"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

libraryDependencies ++= Seq(
  "com.twitter" %% "finatra-http" % "18.9.0",
  "com.twitter" %% "finatra-thrift" % "18.9.0",
  "com.twitter" % "finatra-slf4j_2.12" % "2.12.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.twitter" %% "finagle-http" % "18.9.0",
  "com.github.finagle" %% "finch-core" % "0.22.0",
  "com.github.finagle" %% "finch-circe" % "0.22.0",
  "io.circe" %% "circe-generic" % "0.9.0",
  "org.neo4j.driver" % "neo4j-java-driver" % "1.7.0-beta02"
)