package nebula.plugin.publishing.publications

import groovy.transform.CompileDynamic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar

@CompileDynamic
class SpringBootJarPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.withId('org.springframework.boot') {
            project.plugins.withType(JavaPlugin) {
                project.configurations {
                    [apiElements, runtimeElements].each { Configuration configuration ->
                        def bootJar = project.tasks.named('bootJar')
                        bootJar.configure {
                            def outgoing = configuration.outgoing
                            outgoing.artifacts.removeIf {
                                it.buildDependencies.getDependencies(null)
                                        .contains(project.tasks.named('jar', Jar).get())
                            }
                            outgoing.artifact(bootJar)
                        }
                    }
                }
            }
        }
    }

}
