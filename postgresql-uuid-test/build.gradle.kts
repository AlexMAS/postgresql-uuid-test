dependencies {
    implementation(libs.uuid)
    implementation(libs.postgresql)
}

tasks.register<Copy>("copyLib") {
    from(configurations["runtimeClasspath"]) {
        exclude("org.sandbox.*")
    }
    into("${project.layout.buildDirectory.get()}/libs/lib")
}

tasks.named("assemble") {
    finalizedBy("copyLib")
}
