plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.nexters"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Web scraping
    implementation("org.jsoup:jsoup:1.17.2")
    
    // Google Gemini API (official Java client)
    implementation("com.google.genai:google-genai:1.0.0")
    
    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    
    // Environment variables
    implementation("io.github.cdimascio:dotenv-java:3.0.0")
    
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.nexters.memecrawler.TestOfficialGeminiAPI")
}