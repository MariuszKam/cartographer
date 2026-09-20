import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

val testSourceSet = extensions.getByType<SourceSetContainer>()["test"]

val testArchitecturePatterns = linkedMapOf(
    "SLEEP" to Regex("""\b(?:Thread\.sleep|TimeUnit\.[A-Z]+\.sleep)\s*\("""),
    "SYSTEM_PROPERTY_MUTATION" to Regex("""\bSystem\.(?:setProperty|clearProperty)\s*\("""),
    "SYSTEM_DEFAULT_MUTATION" to Regex("""\b(?:Locale|TimeZone)\.setDefault\s*\("""),
    "USER_HOME_REFERENCE" to Regex("""["']user\.home["']"""),
    "TEMP_DIR" to Regex("""@TempDir\b"""),
    "SQLITE" to Regex("""jdbc:sqlite:"""),
    "FILESYSTEM_MUTATION" to Regex(
        """\bFiles\.(?:write|writeString|createDirectories|createFile|delete|deleteIfExists|move|copy)\s*\("""
    ),
    "EXECUTOR" to Regex("""\bExecutors\."""),
    "THREAD_CREATION" to Regex("""\b(?:new\s+Thread\s*\(|Thread\.of(?:Platform|Virtual)\s*\()"""),
    "UNBOUNDED_THREAD_JOIN" to Regex("""\.join\s*\(\s*\)"""),
    "MUTABLE_STATIC" to Regex(
        """\bstatic\s+(?!final\b)(?!class\b)(?!interface\b)(?!enum\b)[^();{}]+\s+\w+\s*(?:=|;)"""
    ),
    "NETWORK_FIXTURE" to Regex("""\b(?:ServerSocket|HttpServer|localhost|127\.0\.0\.1)\b"""),
    "RESOURCE_LOCK" to Regex("""@ResourceLock\b"""),
    "INTEGRATION_CATEGORY" to Regex("""@IntegrationTest\b"""),
    "CONCURRENCY_CATEGORY" to Regex("""@ConcurrencyTest\b""")
)

tasks.register("testArchitectureAudit") {
    group = "verification"
    description = "Generates a static parallel-safety inventory for the Java test suite"

    val sources = fileTree("src/test/java") {
        include("**/*.java")
    }
    inputs.files(sources)

    val findingsReport = layout.buildDirectory.file(
        "reports/test-performance/test-architecture-audit.tsv"
    )
    val summaryReport = layout.buildDirectory.file(
        "reports/test-performance/test-architecture-summary.csv"
    )
    outputs.files(findingsReport, summaryReport)

    doLast {
        val findingsOutput = findingsReport.get().asFile
        val summaryOutput = summaryReport.get().asFile
        findingsOutput.parentFile.mkdirs()
        summaryOutput.parentFile.mkdirs()

        var findingCount = 0
        val categoriesByFile = sortedMapOf<String, MutableSet<String>>()
        val countByCategory = linkedMapOf<String, Int>()

        findingsOutput.bufferedWriter().use { writer ->
            writer.appendLine("category\tpath\tline\ttext")
            sources.files
                .sortedBy { it.relativeTo(project.projectDir).invariantSeparatorsPath }
                .forEach { source ->
                    val relative = source
                        .relativeTo(project.projectDir)
                        .invariantSeparatorsPath
                    source.readLines().forEachIndexed { index, line ->
                        testArchitecturePatterns.forEach { (category, pattern) ->
                            if (pattern.containsMatchIn(line)) {
                                val normalized = line.trim().replace("\t", " ")
                                writer.appendLine(
                                    "$category\t$relative\t${index + 1}\t$normalized"
                                )
                                categoriesByFile
                                    .getOrPut(relative) { linkedSetOf() }
                                    .add(category)
                                countByCategory[category] =
                                    (countByCategory[category] ?: 0) + 1
                                findingCount++
                            }
                        }
                    }
                }
        }

        summaryOutput.bufferedWriter().use { writer ->
            writer.appendLine(
                "path,signals,reviewSignalCount,reviewPriority,reviewReasons"
            )
            val reviewSignals = setOf(
                "USER_HOME_REFERENCE",
                "EXECUTOR",
                "THREAD_CREATION",
                "NETWORK_FIXTURE",
                "RESOURCE_LOCK"
            )
            categoriesByFile.forEach { (path, categories) ->
                val reviewSignalCount = categories.count { category ->
                    category in reviewSignals
                }
                val reviewReasons = linkedSetOf<String>()
                if ("FILESYSTEM_MUTATION" in categories
                    && "TEMP_DIR" !in categories) {
                    reviewReasons.add("filesystem-without-tempdir")
                }
                if ("SQLITE" in categories && "TEMP_DIR" !in categories) {
                    reviewReasons.add("sqlite-without-tempdir")
                }
                if ("SYSTEM_PROPERTY_MUTATION" in categories
                    || "SYSTEM_DEFAULT_MUTATION" in categories) {
                    reviewReasons.add("process-global-state")
                }
                if ("MUTABLE_STATIC" in categories) {
                    reviewReasons.add("mutable-static")
                }
                if ("SLEEP" in categories) {
                    reviewReasons.add("scheduler-timing")
                }
                if ("UNBOUNDED_THREAD_JOIN" in categories) {
                    reviewReasons.add("unbounded-thread-join")
                }
                if ("THREAD_CREATION" in categories
                    && "CONCURRENCY_CATEGORY" !in categories) {
                    reviewReasons.add("thread-creation-without-concurrency-category")
                }
                if ("EXECUTOR" in categories
                    && "CONCURRENCY_CATEGORY" !in categories) {
                    reviewReasons.add("executor-without-concurrency-category")
                }
                if ("NETWORK_FIXTURE" in categories
                    && "INTEGRATION_CATEGORY" !in categories
                    && "CONCURRENCY_CATEGORY" !in categories) {
                    reviewReasons.add("uncategorized-network-fixture")
                }
                val reviewPriority = when {
                    reviewReasons.isNotEmpty() -> "HIGH"
                    reviewSignalCount > 0 -> "REVIEW"
                    else -> "INFO"
                }
                writer.appendLine(
                    "$path,${categories.sorted().joinToString("|")}," +
                        "$reviewSignalCount,$reviewPriority," +
                        reviewReasons.joinToString("|")
                )
            }
        }

        logger.lifecycle(
            "Test architecture audit: ${findingsOutput.absolutePath} ($findingCount findings)"
        )
        countByCategory.entries
            .sortedByDescending { it.value }
            .forEach { (category, count) ->
                logger.lifecycle("TEST-AUDIT $category=$count")
            }
        logger.lifecycle(
            "Test architecture summary: ${summaryOutput.absolutePath}"
        )
    }
}

tasks.register("testArchitectureGuard") {
    group = "verification"
    description = "Fails when the generated test architecture audit contains HIGH-risk files"
    dependsOn("testArchitectureAudit")

    val summaryReport = layout.buildDirectory.file(
        "reports/test-performance/test-architecture-summary.csv"
    )
    inputs.file(summaryReport)

    doLast {
        val highRiskRows = summaryReport.get().asFile
            .readLines()
            .drop(1)
            .filter { line ->
                line.split(",", limit = 5).getOrNull(3) == "HIGH"
            }

        if (highRiskRows.isNotEmpty()) {
            throw GradleException(
                "HIGH-risk test architecture findings detected:\n" +
                    highRiskRows.joinToString("\n")
            )
        }
        logger.lifecycle("Test architecture guard: PASS (no HIGH-risk files)")
    }
}

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
    mustRunAfter("testArchitectureGuard")
    attachTimingReports("test")
}

val testSuiteBudgetMs = 60_000L
val testClassBudgetMs = 15_000L
val minimumTestCount = 1_039L

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
        if (testCount < minimumTestCount) {
            violations.add(
                "test count $testCount is below minimum $minimumTestCount"
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
    dependsOn("testArchitectureGuard", "testPerformanceBudget")
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
