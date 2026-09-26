import org.gradle.api.GradleException

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


val productionPackagePattern = Regex(
    """^\s*package\s+cartographer\.([A-Za-z0-9_]+)(?:\.[A-Za-z0-9_.]+)?\s*;"""
)
val productionReferencePattern = Regex(
    """\bcartographer\.([A-Za-z0-9_]+)\."""
)

tasks.register("productionArchitectureGuard") {
    group = "verification"
    description = "Fails when production package dependencies are cyclic or violate layer direction"

    val sources = fileTree("src/main/java/cartographer") {
        include("**/*.java")
    }
    inputs.files(sources)

    val graphReport = layout.buildDirectory.file(
        "reports/architecture/production-package-graph.txt"
    )
    outputs.file(graphReport)

    doLast {
        val graph = sortedMapOf<String, MutableSet<String>>()
        val edgeSources =
            linkedMapOf<Pair<String, String>, MutableSet<String>>()

        sources.files
            .sortedBy {
                it.relativeTo(project.projectDir).invariantSeparatorsPath
            }
            .forEach { source ->
                val relative = source
                    .relativeTo(project.projectDir)
                    .invariantSeparatorsPath
                val lines = source.readLines()
                val sourcePackage = lines.asSequence()
                    .mapNotNull { line ->
                        productionPackagePattern.find(line)
                            ?.groupValues
                            ?.get(1)
                    }
                    .firstOrNull()
                    ?: return@forEach

                graph.getOrPut(sourcePackage) { sortedSetOf() }

                lines.forEach { line ->
                    productionReferencePattern.findAll(line)
                        .map { match -> match.groupValues[1] }
                        .filter { targetPackage ->
                            targetPackage != sourcePackage
                        }
                        .forEach { targetPackage ->
                            graph.getOrPut(sourcePackage) { sortedSetOf() }
                                .add(targetPackage)
                            graph.getOrPut(targetPackage) { sortedSetOf() }
                            edgeSources
                                .getOrPut(
                                    sourcePackage to targetPackage
                                ) { sortedSetOf() }
                                .add(relative)
                        }
                }
            }

        val state = mutableMapOf<String, Int>()
        val stack = mutableListOf<String>()
        val cycles = linkedSetOf<String>()

        fun visit(node: String) {
            state[node] = 1
            stack.add(node)

            graph[node].orEmpty().sorted().forEach { target ->
                when (state[target] ?: 0) {
                    0 -> visit(target)
                    1 -> {
                        val start = stack.indexOf(target)
                        if (start >= 0) {
                            cycles.add(
                                (
                                    stack.subList(start, stack.size) +
                                        target
                                ).joinToString(" -> ")
                            )
                        }
                    }
                }
            }

            stack.removeAt(stack.lastIndex)
            state[node] = 2
        }

        graph.keys.sorted().forEach { node ->
            if ((state[node] ?: 0) == 0) {
                visit(node)
            }
        }

        val forbidden = mutableListOf<String>()
        edgeSources.entries
            .sortedWith(
                compareBy<
                    Map.Entry<
                        Pair<String, String>,
                        MutableSet<String>
                    >
                >(
                    { it.key.first },
                    { it.key.second }
                )
            )
            .forEach { (edge, filesForEdge) ->
                val (sourcePackage, targetPackage) = edge
                val reason = when {
                    targetPackage == "ui"
                            && sourcePackage != "ui" ->
                        "production packages outside ui must not depend on ui"

                    targetPackage == "application"
                            && sourcePackage != "application"
                            && sourcePackage != "ui" ->
                        "lower-level packages must not depend on application"

                    sourcePackage == "snapshot"
                            && targetPackage in setOf(
                                "application",
                                "render",
                                "prospecting"
                            ) ->
                        "snapshot must not depend on application/render/prospecting"

                    sourcePackage == "parser"
                            && targetPackage == "save" ->
                        "parser must not depend on save-owned infrastructure"

                    else -> null
                }

                if (reason != null) {
                    forbidden.add(
                        "${sourcePackage} -> ${targetPackage}: ${reason} " +
                            "[${filesForEdge.joinToString(", ")}]"
                    )
                }
            }

        val report = graphReport.get().asFile
        report.parentFile.mkdirs()
        report.bufferedWriter().use { writer ->
            writer.appendLine("Production package dependency graph")
            writer.appendLine()
            graph.forEach { (sourcePackage, targets) ->
                writer.appendLine(
                    "${sourcePackage} -> " +
                        targets.sorted().joinToString(", ")
                )
            }
            writer.appendLine()
            writer.appendLine(
                "Cycles: " +
                    if (cycles.isEmpty()) "0" else cycles.size
            )
            cycles.forEach { cycle ->
                writer.appendLine("CYCLE ${cycle}")
            }
            writer.appendLine(
                "Forbidden edges: " +
                    forbidden.size
            )
            forbidden.forEach { violation ->
                writer.appendLine("FORBIDDEN ${violation}")
            }
        }

        val violations = mutableListOf<String>()
        cycles.forEach { cycle ->
            violations.add("package cycle: ${cycle}")
        }
        forbidden.forEach { violation ->
            violations.add("forbidden dependency: ${violation}")
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Production architecture guard failed:\n" +
                    violations.joinToString("\n") +
                    "\nReport: ${report.absolutePath}"
            )
        }

        logger.lifecycle(
            "Production architecture guard: PASS " +
                "(${graph.size} packages, " +
                "${edgeSources.size} edges, 0 cycles, " +
                "0 forbidden dependencies)"
        )
        logger.lifecycle(
            "Production package graph: ${report.absolutePath}"
        )
    }
}

