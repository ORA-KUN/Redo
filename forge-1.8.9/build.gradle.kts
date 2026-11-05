import org.apache.commons.lang3.SystemUtils

plugins {
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val baseGroup: String by project
val mcVersion: String by project
val version: String by project
val modid: String by project

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

loom {
    log4jConfigs.from(file("log4j2.xml"))
    launchConfigs {
        "client" {
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) vmArgs.remove("-XstartOnFirstThread")
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("mixins.$modid.json")
        accessTransformer(file("src/main/resources/accesstransformer.cfg"))
    }
    mixin {
        defaultRefmapName.set("mixins.$modid.refmap.json")
    }
}

sourceSets.main {
    // The shared core (no Minecraft code) is compiled straight into this jar.
    java.srcDir("../core/src/main/java")
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
}

val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}

tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set("redo")
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "mixins.$modid.json"
        this["FMLAT"] = "${modid}_at.cfg"
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)
    inputs.property("basePackage", baseGroup)
    filesMatching(listOf("mcmod.info", "mixins.$modid.json")) {
        expand(inputs.properties)
    }
    rename("accesstransformer.cfg", "META-INF/${modid}_at.cfg")
}

val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    archiveVersion.set("${project.version}+1.8.9")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}

tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
}

tasks.assemble.get().dependsOn(tasks.remapJar)

// Prints the signatures of chosen Minecraft classes (used to write the version-specific code).
tasks.register("redoProbe") {
    group = "help"
    val classesFile = rootProject.file("../.github/probe/forge-classes.txt")
    doLast {
        val jars = configurations.getByName("compileClasspath").files.filter {
            (it.name.contains("minecraft") || it.name.contains("forge")) && it.name.endsWith(".jar")
        }
        println("PROBE 1.8.9 jars: " + jars.joinToString { it.name })
        val out = layout.buildDirectory.file("probe/1.8.9.txt").get().asFile
        out.parentFile.mkdirs()
        val wanted = if (classesFile.exists()) classesFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") } else emptyList()
        val sb = StringBuilder()
        val cpString = jars.joinToString(File.pathSeparator) { it.absolutePath }
        for (line in wanted) {
            if (line.startsWith("grep ")) {
                val regex = Regex(line.removePrefix("grep ").trim())
                jars.forEach { jar ->
                    java.util.zip.ZipFile(jar).use { zip ->
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
