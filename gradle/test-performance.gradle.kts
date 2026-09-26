import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

val testSourceSet = extensions.getByType<SourceSetContainer>()["test"]

fun Test.attachTimingReports(reportPrefix: String) {
    val classDurations = ConcurrentHashMap<String, Long>()
    val methodDurations = ConcurrentHashMap<String, Long>()

    fun csvCell(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) = Unit

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            val className = suite.className
            if (className != null && suite.parent?.className == null) {
                classDurations[className] = result.endTime - result.startTime
            }

            if (suite.parent == null) {
                val reportDirectory = layout.buildDirectory
                    .dir("reports/test-performance")
                    .get()
                    .asFile
                reportDirectory.mkdirs()

                val classOutput = File(
                    reportDirectory,
                    "$reportPrefix-class-timings.csv"
                )
                classOutput.bufferedWriter().use { writer ->
                    writer.appendLine("class,durationMs")
                    classDurations.entries
                        .sortedByDescending { it.value }
                        .forEach { (testClass, durationMs) ->
                            writer.appendLine("${csvCell(testClass)},$durationMs")
                        }
                }

                val methodOutput = File(
                    reportDirectory,
                    "$reportPrefix-method-timings.csv"
                )
                methodOutput.bufferedWriter().use { writer ->
                    writer.appendLine("test,durationMs")
                    methodDurations.entries
                        .sortedByDescending { it.value }
                        .forEach { (testName, durationMs) ->
                            writer.appendLine("${csvCell(testName)},$durationMs")
                        }
                }

                val summaryOutput = File(
                    reportDirectory,
                    "$reportPrefix-suite-summary.csv"
                )
                summaryOutput.bufferedWriter().use { writer ->
                    writer.appendLine("metric,value")
                    writer.appendLine("durationMs,${result.endTime - result.startTime}")
                    writer.appendLine("testCount,${result.testCount}")
                    writer.appendLine(
                        "successfulTestCount,${result.successfulTestCount}"
                    )
                    writer.appendLine("failedTestCount,${result.failedTestCount}")
                    writer.appendLine("skippedTestCount,${result.skippedTestCount}")
                    writer.appendLine("maxParallelForks,$maxParallelForks")
                }

                logger.lifecycle(
                    "Test class timing report: ${classOutput.absolutePath}"
                )
                classDurations.entries
                    .sortedByDescending { it.value }
                    .take(20)
                    .forEachIndexed { index, entry ->
                        logger.lifecycle(
                            "TEST-CLASS-TIMING #${index + 1} " +
                                "${entry.value} ms ${entry.key}"
                        )
                    }

                logger.lifecycle(
                    "Test method timing report: ${methodOutput.absolutePath}"
                )
                methodDurations.entries
                    .sortedByDescending { it.value }
                    .take(30)
                    .forEachIndexed { index, entry ->
                        logger.lifecycle(
                            "TEST-METHOD-TIMING #${index + 1} " +
                                "${entry.value} ms ${entry.key}"
                        )
                    }
            }
        }

        override fun beforeTest(testDescriptor: TestDescriptor) = Unit

        override fun afterTest(
            testDescriptor: TestDescriptor,
            result: TestResult
        ) {
            val className = testDescriptor.className ?: return
            val testName = "$className#${testDescriptor.name}"
            val durationMs = result.endTime - result.startTime
            methodDurations.merge(testName, durationMs) { left, right ->
                left + right
            }
        }
    })
}

fun Test.disableInProcessJUnitParallelism() {
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    disableInProcessJUnitParallelism()
    reports.junitXml.required.set(true)
    reports.html.required.set(true)
    maxParallelForks = 1
    mustRunAfter("testArchitectureGuard", "productionArchitectureGuard")
    attachTimingReports("test")
}

val testSuiteBudgetMs = 60_000L
val testClassBudgetMs = 15_000L

tasks.register("testPerformanceBudget") {
    group = "verification"
    description = "Checks coarse regression budgets for the complete test suite"
    dependsOn(tasks.named("test"))

    val reportDirectory = layout.buildDirectory.dir("reports/test-performance")
    val suiteSummary = reportDirectory.map { it.file("test-suite-summary.csv") }
    val classTimings = reportDirectory.map { it.file("test-class-timings.csv") }
    inputs.files(suiteSummary, classTimings)

    doLast {
        val metrics = suiteSummary.get().asFile
            .readLines()
            .drop(1)
            .mapNotNull { line ->
                val parts = line.split(",", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()

        fun metric(name: String): Long =
            metrics[name]?.toLongOrNull()
                ?: throw GradleException("Missing numeric test metric '$name'")

        val durationMs = metric("durationMs")
        val testCount = metric("testCount")
        val successfulTestCount = metric("successfulTestCount")
        val failedTestCount = metric("failedTestCount")
        val skippedTestCount = metric("skippedTestCount")

        val slowClasses = classTimings.get().asFile
            .readLines()
            .drop(1)
            .mapNotNull { line ->
                val split = line.lastIndexOf(',')
                if (split <= 0) {
                    null
                } else {
                    val className = line.substring(0, split)
                        .trim()
                        .removeSurrounding("\"")
                        .replace("\"\"", "\"")
                    val classDurationMs = line.substring(split + 1).toLongOrNull()
                    classDurationMs?.let { className to it }
                }
            }
            .filter { (_, classDurationMs) ->
                classDurationMs > testClassBudgetMs
            }

        val violations = mutableListOf<String>()
        if (durationMs > testSuiteBudgetMs) {
            violations.add(
                "suite duration ${durationMs}ms exceeds ${testSuiteBudgetMs}ms"
            )
        }
        if (successfulTestCount != testCount
            || failedTestCount != 0L
            || skippedTestCount != 0L) {
            violations.add(
                "suite completeness mismatch: total=$testCount, " +
                    "successful=$successfulTestCount, failed=$failedTestCount, " +
                    "skipped=$skippedTestCount"
            )
        }
        slowClasses.forEach { (className, classDurationMs) ->
            violations.add(
                "test class $className took ${classDurationMs}ms, " +
                    "budget is ${testClassBudgetMs}ms"
            )
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Test performance budget failed:\n" +
                    violations.joinToString("\n")
            )
        }

        logger.lifecycle(
            "Test performance budget: PASS " +
                "(suite=${durationMs}ms/${testSuiteBudgetMs}ms, " +
                "tests=$testCount, classBudget=${testClassBudgetMs}ms)"
        )
    }
}

tasks.register("testQualityGate") {
    group = "verification"
    description = "Runs the complete pull-request test quality gate"
    dependsOn(
        "productionArchitectureGuard",
        "testArchitectureGuard",
        "testPerformanceBudget"
    )
}

val detectedTestCpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
val defaultParallelProbeForks = if (detectedTestCpuCount <= 2) {
    1
} else {
    (detectedTestCpuCount / 2).coerceAtMost(4)
}
val configuredParallelProbeForks = providers.gradleProperty("testParallelForks")
    .map { raw ->
        raw.toIntOrNull()
            ?.takeIf { it in 1..16 }
            ?: throw GradleException(
                "testParallelForks must be an integer between 1 and 16, got '$raw'"
            )
    }
    .orElse(defaultParallelProbeForks)

tasks.register<Test>("testParallelProbe") {
    group = "verification"
    dependsOn("testClasses")
    description = "Runs the JUnit suite with bounded Gradle worker-process parallelism"
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform()
    disableInProcessJUnitParallelism()
    maxParallelForks = configuredParallelProbeForks.get()
    reports.junitXml.required.set(true)
    reports.html.required.set(true)
    attachTimingReports("test-parallel-probe")

    doFirst {
        logger.lifecycle(
            "Parallel probe: maxParallelForks=$maxParallelForks, " +
                "detectedProcessors=$detectedTestCpuCount"
        )
    }
}

fun registerTaggedTestTask(
    taskName: String,
    taskDescription: String,
    tag: String
) {
    tasks.register<Test>(taskName) {
        group = "verification"
        description = taskDescription
        dependsOn("testClasses")
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        useJUnitPlatform {
            includeTags(tag)
        }
        disableInProcessJUnitParallelism()
        maxParallelForks = 1
        reports.junitXml.required.set(true)
        reports.html.required.set(true)
    }
}

registerTaggedTestTask(
    "testIntegration",
    "Runs tests explicitly categorized as integration",
    "integration"
)
registerTaggedTestTask(
    "testConcurrency",
    "Runs tests explicitly categorized as concurrency/lifecycle",
    "concurrency"
)
