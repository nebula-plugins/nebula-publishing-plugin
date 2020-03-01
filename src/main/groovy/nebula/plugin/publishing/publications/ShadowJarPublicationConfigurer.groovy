package nebula.plugin.publishing.publications

import groovy.transform.CompileDynamic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.jvm.tasks.Jar

@CompileDynamic
class ShadowJarPublicationConfigurer {

    static configureForJarDisabled(Project project) {
        Task shadowJarTask = project.tasks.findByName('shadowJar')
        project.configurations {
            [apiElements, runtimeElements].each {
                if (shadowJarTask) {
                    it.outgoing.artifacts.removeIf { it.buildDependencies.getDependencies(null).contains(project.tasks.named('jar', Jar).get()) }
                    it.outgoing.artifact(shadowJarTask)
                }
            }
        }
    }
}
