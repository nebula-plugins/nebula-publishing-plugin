package nebula.plugin.publishing.publications

import groovy.transform.CompileDynamic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.maven.MavenPublication
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
                            Task shadowJarTask = project.tasks.findByName('shadowJar')
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
                            publications.withType(MavenPublication) { MavenPublication publication ->
                                publication.artifact(project.tasks.findByName('shadowJar'))
                            }
                            publications.withType(IvyPublication) { IvyPublication publication ->
                                publication.artifact(project.tasks.findByName('shadowJar'))
                            }
                        }
                    })
                }
            }
        }
    }
}
