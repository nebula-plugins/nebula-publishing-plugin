package nebula.plugin.publishing.publications

import groovy.transform.CompileDynamic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

@CompileDynamic
class ShadowJarPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.afterEvaluate {
            project.plugins.withId('com.github.johnrengelman.shadow') {
                boolean jarTaskEnabled = project.tasks.findByName('jar').enabled
                if(!jarTaskEnabled) {
                    project.configurations {
                        [apiElements, runtimeElements].each {
                            if(project.tasks.named('shadowJar', Jar).isPresent()) {
                                it.outgoing.artifacts.removeIf { it.buildDependencies.getDependencies(null).contains(project.tasks.named('jar', Jar).get()) }
                                it.outgoing.artifact(project.tasks.named('shadowJar', Jar).get())
                            }
                        }
                    }
                }
            }
        }
    }
}
