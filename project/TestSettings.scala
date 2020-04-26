import sbt._
import sbt.Keys._
import xsbti.compile.CompileAnalysis
import xsbti.api.{Definition, Projection}

object TestSettings {
  def forked(scope: Configuration): Seq[Setting[_]] = Seq(
    fork in scope := true,
    javaOptions in scope ++= Seq(
      "-Xms256M", "-Xmx512M",
      "-Dconfig.file=toh-lagom-java-impl/src/it/resources/application.conf"
    ),
    testGrouping in scope := groupTests(
      (compile in scope).value,
      (definedTests in scope).value,
      (javaOptions in scope).value)
  )

  private def groupTests(analysis: CompileAnalysis, tests: Seq[TestDefinition],
                         javaOptions: Seq[String]): Seq[Tests.Group] = {
    val cassandraTestNames: Set[String] = Tests.allDefs(analysis)
      .withFilter(hasAnnotation("RequiresCassandra")).map(_.name).toSet
    val (cassandraTests, otherTests) = tests.partition(test => cassandraTestNames.contains(test.name))
    val runPolicy = Tests.SubProcess(ForkOptions().withRunJVMOptions(javaOptions.toVector))
    val cassandraTestGroups = cassandraTests.map(test => Tests.Group(test.name, Seq(test), runPolicy))
    val otherTestsGroup = Tests.Group("Other tests", otherTests, runPolicy)
    otherTestsGroup +: cassandraTestGroups
  }

  def hasAnnotation(annotationId: String): Definition => Boolean =
    definition => definition.annotations.exists(_.base match {
      case proj: Projection => proj.id == annotationId
      case _ => false
    })
}
