import scala.util.Random

name in ThisBuild := "toh-lagom-java"
scalaVersion in ThisBuild := "2.13.1"
organization in ThisBuild := "com.chariotsolutions"
maintainer in ThisBuild := "lkorogodski@chariotsolutions.com"
version in ThisBuild := s"""${sys.env.getOrElse("TOH_VERSION", "1.0.0")}"""

javacOptions in Compile += "-parameters"

// These settings will only be used when running in the Dev mode.
// Disable the embedded Cassandra service and use AWS MCS instead.
// Disable the embedded Kafka server, too.
lagomKafkaEnabled in ThisBuild := false
lagomCassandraEnabled in ThisBuild := false
lagomUnmanagedServices in ThisBuild := Map(
  "cas_native" -> s"https://${CassandraUtils.contactPoint}:${CassandraUtils.port}"
)

val kubernetesApi = "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % "1.0.5"
val lagomSvcRegistry = "com.lightbend.lagom" %% "lagom-service-registry-client" % "1.6.1"
val lagomTestKit = "com.lightbend.lagom" %% "lagom-javadsl-testkit" % "1.6.1"
val postgresql = "org.postgresql" % "postgresql" % "42.2.10"
val novocode = "com.novocode" % "junit-interface" % "0.11" // for running JUnit from SBT
val lombok = "org.projectlombok" % "lombok" % "1.18.8"
val h2 = "com.h2database" % "h2" % "1.4.200"
val junit = "junit" % "junit" % "4.13"

lazy val `toh-lagom-java` = (project in file("."))
  .aggregate(`toh-lagom-java-api`, `toh-lagom-java-impl`)

lazy val `toh-lagom-java-api` = (project in file("toh-lagom-java-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslApi,
      lombok
    )
  )

lazy val `toh-lagom-java-impl` = (project in file("toh-lagom-java-impl"))
  .enablePlugins(LagomScala, EcrPlugin)
  .dependsOn(`toh-lagom-java-api`)
  .configs(IntegrationTest)
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslServer,
      lagomSvcRegistry % Runtime,
      lagomJavadslPersistenceCassandra,
      lagomJavadslPersistenceJdbc,
      lagomJavadslAkkaDiscovery,
      kubernetesApi % Runtime,
      postgresql % Runtime
    )
  )
  .settings(
    Defaults.itSettings,
    TestSettings.forked(IntegrationTest),
    testOptions in Test += Tests.Argument(TestFrameworks.JUnit, "-q", "-v"),
    testOptions in IntegrationTest += Tests.Argument(TestFrameworks.JUnit, "-q", "-v"),
    libraryDependencies ++= Seq(
      junit % Test,
      novocode % Test,
      novocode % IntegrationTest,
      lagomTestKit % IntegrationTest,
      h2 % IntegrationTest
    )
  )
  .settings(
    packageName in Docker := (name in ThisBuild).value,
    dockerExposedPorts in Docker := Seq(9000, 9008, 8558, 2552, 25520),
    mappings in Universal += file("cassandra_truststore.jks") -> "cassandra_truststore.jks",
    javaOptions in Universal ++= Seq(
      "-Dpidfile.path=/dev/null",
      "-Dconfig.resource=production.conf",
      s"-Dplay.http.secret.key=${Random.alphanumeric.take(40).mkString}",
      s"-Dcassandra.default.authentication.username=${AmazonUtils.cassandraCredentials.username}",
      s"-Dcassandra.default.authentication.password=${AmazonUtils.cassandraCredentials.password}",
      s"""-Ddb.default.url=${sys.env.getOrElse("POSTGRESQL_URL", "jdbc:postgresql://localhost/toh_lagom")}""",
      s"-Ddb.default.username=${AmazonUtils.postgresqlCredentials.username}",
      s"-Ddb.default.password=${AmazonUtils.postgresqlCredentials.password}"
    )
  )
  .settings(
    region in Ecr := AmazonUtils.awsRegion,
    repositoryTags in Ecr ++= Seq(version.value),
    repositoryName in Ecr := (packageName in Docker).value,
    login in Ecr := ((login in Ecr) dependsOn (createRepository in Ecr)).value,
    push in Ecr := ((push in Ecr) dependsOn (publishLocal in Docker, login in Ecr)).value,
    localDockerImage in Ecr := s"${(packageName in Docker).value}:${(version in Docker).value}"
  )

lazy val initializeSchema = taskKey[Unit]("Initializes Cassandra schema")
initializeSchema := CassandraUtils.initializeSchema()
