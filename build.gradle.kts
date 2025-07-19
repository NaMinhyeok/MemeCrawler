plugins {
    id("java")
    id("application")
}

group = "org.nexters"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Web scraping
    implementation("org.jsoup:jsoup:1.17.2")
    
    // Google Vertex AI
    implementation("com.google.cloud:google-cloud-aiplatform:3.44.0")
    
    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.nexters.memecrawler.CleanTextCrawler")
}