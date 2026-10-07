plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    // PaperMC : fournit l'API de Velocity
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    // Fournit aussi Adventure/MiniMessage, SnakeYAML, Gson, Guice et SLF4J, présents sur le proxy
    compileOnly("com.velocitypowered:velocity-api:4.2.0")
    // Génère velocity-plugin.json à partir de l'annotation @Plugin
    annotationProcessor("com.velocitypowered:velocity-api:4.2.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}

// Les plugins proxy compilent contre EterVelocityLib : com.github.Eternom:EterVelocityLib:<version>.
// - n'importe où : JitPack compile le tag GitHub correspondant (voir jitpack.yml) ;
// - en local : `gradlew publishToMavenLocal` publie sous les mêmes coordonnées, pour tester avant de pousser.
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "EterVelocityLib"
            from(components["java"])
        }
    }
}

// Après chaque build, copie le jar dans <eterPluginsDir>/velocity et y supprime l'ancienne version de ce plugin.
// eterPluginsDir se règle dans ~/.gradle/gradle.properties (propre à ta machine) : sans lui, rien n'est copié.
val deployPlugin by tasks.registering(Copy::class) {
    description = "Copie le jar dans le dossier de plugins local (eterPluginsDir)"
    // Variables locales à la tâche : le cache de configuration de Gradle refuse les variables du script
    val eterPluginsDir = providers.gradleProperty("eterPluginsDir")
    val enabled = eterPluginsDir.isPresent
    onlyIf { enabled }
    val jarName = tasks.jar.flatMap { it.archiveBaseName }
    from(tasks.jar)
    into(eterPluginsDir.map { "$it/velocity" }.orElse(layout.buildDirectory.dir("deploy").map { it.asFile.path }))
    doFirst {
        destinationDir.listFiles { file -> file.name.startsWith(jarName.get() + "-") && file.name.endsWith(".jar") }
            ?.forEach { it.delete() }
    }
}
tasks.build { finalizedBy(deployPlugin) }
