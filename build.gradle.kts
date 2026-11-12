plugins {
    java
}

group = "kr.koyo.requestpruner"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("net.portswigger.burp.extensions:montoya-api:2026.7")
    compileOnly("net.portswigger.burp.extensions:montoya-api:2026.7")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.jar {
    archiveVersion = ""
    manifest { attributes("Implementation-Version" to version) }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}
