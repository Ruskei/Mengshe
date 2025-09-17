plugins {
    kotlin("jvm") version "2.2.0"
}

group = "com.ixume"
version = "0.0.3.4"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}