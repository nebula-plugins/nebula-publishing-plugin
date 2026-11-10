package nebula.plugin.publishing.publications

import groovy.transform.CompileDynamic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.jvm.tasks.Jar

/** Replaces the main Java artifact when a project publishes only its Shadow JAR. */
@CompileDynamic
class ShadowJarPublicationConfigurer {

    static configureForJarDisabled(Project project) {
        Task shadowJarTask = project.tasks.findByName('shadowJar')
        if (project.providers.gradleProperty('nebula.publishing.isolatedProjectsSupport')
                .map { Boolean.parseBoolean(it) }.getOrElse(false)) {
            configureIsolated(project, shadowJarTask)
            return
        }
        project.configurations {
            [apiElements, runtimeElements].each {
                if (shadowJarTask) {
                    it.outgoing.artifacts.removeIf { it.buildDependencies.getDependencies(null).contains(project.tasks.named('jar', Jar).get()) }
                    it.outgoing.artifact(shadowJarTask)
                }
            }
        }
    }

    /**
     * Identifies the main artifact by the local Jar task's output. Resolving an
     * artifact's build dependencies during configuration is forbidden by Isolated
     * Projects, even when the artifact belongs to the current project. Preserve
     * other outgoing artifacts and let Gradle wire the Shadow task dependency.
     */
    private static void configureIsolated(Project project, Task shadowJarTask) {
        if (shadowJarTask == null) {
            return
        }
        File jarFile = project.tasks.named('jar', Jar).get().archiveFile.get().asFile
        ['apiElements', 'runtimeElements'].each { String name ->
            project.configurations.named(name).configure { Configuration configuration ->
                configuration.outgoing.artifacts.removeIf { PublishArtifact artifact -> artifact.file == jarFile }
                configuration.outgoing.artifact(shadowJarTask)
            }
        }
    }
}
