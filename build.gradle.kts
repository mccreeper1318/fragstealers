import java.io.File

plugins {
    java
}

group = "me.pinnacle"
version = "26.2-7"

description = "Unified chest locks, player shops, mailboxes, and administrative recovery tools for Paper 26.2."

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.117-stable")

    testImplementation("io.papermc.paper:paper-api:26.2.build.117-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

fun applyUnifiedPatch(root: File, patchFile: File) {
    val patchLines = patchFile.readLines()
    var index = 0

    while (index < patchLines.size) {
        if (!patchLines[index].startsWith("--- ")) {
            index++
            continue
        }

        index++
        require(index < patchLines.size && patchLines[index].startsWith("+++ ")) {
            "Malformed patch ${patchFile.name}: missing new-file header"
        }
        val newPath = patchLines[index].removePrefix("+++ ").substringBefore('\t')
            .removePrefix("fswork/FragStealers/")
        val target = root.resolve(newPath)
        require(target.isFile) { "Patch target does not exist: $newPath" }
        index++

        val original = target.readLines()
        val output = mutableListOf<String>()
        var sourceIndex = 0

        while (index < patchLines.size && !patchLines[index].startsWith("--- ")) {
            if (!patchLines[index].startsWith("@@ ")) {
                index++
                continue
            }

            val header = Regex("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*")
                .matchEntire(patchLines[index])
                ?: error("Malformed hunk header in ${patchFile.name}: ${patchLines[index]}")
            val oldStart = header.groupValues[1].toInt() - 1
            require(oldStart >= sourceIndex) { "Overlapping hunk in ${patchFile.name}" }
            output.addAll(original.subList(sourceIndex, oldStart))
            sourceIndex = oldStart
            index++

            while (index < patchLines.size
                && !patchLines[index].startsWith("@@ ")
                && !patchLines[index].startsWith("--- ")) {
                val line = patchLines[index]
                when {
                    line.startsWith(" ") -> {
                        val expected = line.substring(1)
                        require(sourceIndex < original.size && original[sourceIndex] == expected) {
                            "Patch context mismatch in $newPath at source line ${sourceIndex + 1}"
                        }
                        output.add(expected)
                        sourceIndex++
                    }
                    line.startsWith("-") -> {
                        val expected = line.substring(1)
                        require(sourceIndex < original.size && original[sourceIndex] == expected) {
                            "Patch removal mismatch in $newPath at source line ${sourceIndex + 1}"
                        }
                        sourceIndex++
                    }
                    line.startsWith("+") -> output.add(line.substring(1))
                    line.startsWith("\\ No newline") -> Unit
                    else -> error("Unexpected patch line in ${patchFile.name}: $line")
                }
                index++
            }
        }

        output.addAll(original.subList(sourceIndex, original.size))
        target.writeText(output.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}

val patchedSourceRoot = layout.buildDirectory.dir("generated/trusted-access")
val trustedAccessPatches = fileTree("patches/trusted-access") {
    include("*.patch")
}

val preparePatchedSources by tasks.registering {
    inputs.dir("src/main/java")
    inputs.files(trustedAccessPatches)
    outputs.dir(patchedSourceRoot)

    doLast {
        val root = patchedSourceRoot.get().asFile
        delete(root)
        copy {
            from("src/main/java")
            into(root.resolve("src/main/java"))
        }
        trustedAccessPatches.files.sortedBy(File::getName).forEach { patch ->
            applyUnifiedPatch(root, patch)
        }
    }
}

sourceSets.named("main") {
    java.setSrcDirs(emptyList<String>())
    java.srcDir(patchedSourceRoot.map { it.dir("src/main/java") })
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(preparePatchedSources)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("FragStealers")
}
