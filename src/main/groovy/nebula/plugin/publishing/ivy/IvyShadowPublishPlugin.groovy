package nebula.plugin.publishing.ivy

import groovy.transform.CompileDynamic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.jvm.tasks.Jar

@CompileDynamic
class IvyShadowPublishPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.afterEvaluate {
            project.plugins.withId('com.github.johnrengelman.shadow') {
                Task shadowJarTask = project.tasks.findByName('shadowJar')
                boolean jarTaskEnabled = project.tasks.findByName('jar').enabled
                if(!jarTaskEnabled) {
                    project.configurations {
                        [apiElements, runtimeElements].each {
                            if(shadowJarTask) {
                                it.outgoing.artifacts.removeIf { it.buildDependencies.getDependencies(null).contains(project.tasks.named('jar', Jar).get()) }
                                it.outgoing.artifact(shadowJarTask)
                            }
                        }
                    }
                } else {
                    PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
                    publishing.publications(new Action<PublicationContainer>() {
                        @Override
                        void execute(PublicationContainer publications) {
                            if(shadowJarTask) {
                                publications.withType(IvyPublication) { IvyPublication publication ->
                                    publication.artifact(shadowJarTask)
                                }
                            }
                        }
                    })
                }
            }
        }
    }
}
