plugins {
    kotlin("jvm") version "2.2.0"
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "com.ixume"
version = "0.0.3.21"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    compileOnly(files("gradle/build/libs/fastutil-8.5.16.jar"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}