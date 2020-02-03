package nebula.plugin.publishing.publications

import groovy.transform.CompileDynamic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

@CompileDynamic
class SpringBootJarPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.withId('org.springframework.boot') {
            project.configurations {
                [apiElements, runtimeElements].each {
                    it.outgoing.artifacts.removeIf { it.buildDependencies.getDependencies(null).contains(project.tasks.named('jar', Jar).get()) }
                    it.outgoing.artifact(project.tasks.named('bootJar', Jar).get())
                }
            }
        }
    }
}
