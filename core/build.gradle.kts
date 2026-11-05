// Standalone build of the shared core (no Minecraft). The platform builds
// (forge-1.8.9 and fabric) compile these sources directly into their jars.
plugins {
    java
}

group = "fr.orakun.redo"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.addAll(listOf("-Xlint:-options"))
}

tasks.test {
    useJUnitPlatform()
}
