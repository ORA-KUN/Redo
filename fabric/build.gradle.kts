import java.util.zip.ZipFile

plugins {
    // Applies the right Loom variant for the Minecraft version (26.1+ is unobfuscated)
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    // Redo uses only mixins: no Fabric API needed
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
}

sourceSets.main {
    // The shared core (no Minecraft code) is compiled straight into each jar.
    java.srcDir(rootProject.file("../core/src/main/java"))
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    accessWidenerPath = sc.process(rootProject.file("src/main/resources/redo.ct"), "build/processed.ct")
    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
    }
}

java {
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    processResources {
        val props = mapOf(
            "id" to sc.properties.get<String>("mod.id"),
            "name" to sc.properties.get<String>("mod.name"),
            "version" to sc.properties.get<String>("mod.version"),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader" to Regex("\\d+\\.\\d+").find(sc.properties.get<String>("deps.fabric_loader"))!!.value,
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching("fabric.mod.json") { expand(props) }
        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        inputs.property("java", mixinJava)
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to build/libs/<mod version>/"
        inputs.property("version", project.property("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }

    // Prints the signatures of chosen Minecraft classes (used to write the version-specific code).
    register("redoProbe") {
        group = "help"
        val classesFile = rootProject.file("../.github/probe/fabric-classes.txt")
        val mcVersion = sc.current.version
        val cp = configurations.named("compileClasspath")
        doLast {
            val jars = cp.get().files.filter { it.name.contains("minecraft") && it.name.endsWith(".jar") }
            println("PROBE $mcVersion jars: " + jars.joinToString { it.name })
            val out = rootProject.layout.buildDirectory.file("probe/$mcVersion.txt").get().asFile
            out.parentFile.mkdirs()
            val wanted = if (classesFile.exists()) classesFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") } else emptyList()
            val sb = StringBuilder()
            val cpString = jars.joinToString(File.pathSeparator) { it.absolutePath }
            for (line in wanted) {
                if (line.startsWith("grep ")) {
                    // "grep <regex>": lists the class names matching the regex
                    val regex = Regex(line.removePrefix("grep ").trim())
                    jars.forEach { jar ->
                        ZipFile(jar).use { zip ->
                            zip.entries().asSequence().map { it.name }.filter { it.endsWith(".class") && regex.containsMatchIn(it) }
                                .forEach { sb.append("CLASS ").append(it.removeSuffix(".class").replace('/', '.')).append('\n') }
                        }
                    }
                    continue
                }
                // "Class :: regex" keeps only the members matching the regex
                val cls = line.substringBefore(" :: ").trim()
                val filter = if (line.contains(" :: ")) Regex(line.substringAfter(" :: ").trim()) else null
                val proc = ProcessBuilder("javap", "-p", "-cp", cpString, cls).redirectErrorStream(true).start()
                val text = proc.inputStream.bufferedReader().readLines()
                proc.waitFor()
                text.filter { filter == null || it.contains(" class ") || it.contains(" interface ") || it.startsWith("Error") || filter.containsMatchIn(it) }
                    .forEach { sb.append(it).append('\n') }
                sb.append('\n')
            }
            out.writeText(sb.toString())
            println("PROBE written ${out.absolutePath} (${sb.length} chars)")
        }
    }
}
