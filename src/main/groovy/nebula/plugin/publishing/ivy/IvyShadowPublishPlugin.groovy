package nebula.plugin.publishing.ivy

import groovy.transform.CompileDynamic
import nebula.plugin.publishing.publications.ShadowJarPublicationConfigurer
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyArtifact
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
                if (!jarTaskEnabled) {
                    ShadowJarPublicationConfigurer.configureForJarDisabled(project)
                } else {
                    PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
                    publishing.publications(new Action<PublicationContainer>() {
                        @Override
                        void execute(PublicationContainer publications) {
                            if (shadowJarTask) {
                                publications.withType(IvyPublication) { IvyPublication publication ->
                                    publication.configurations {
                                        'shadow' { }
                                    }
                                    publication.artifact(shadowJarTask, new Action<IvyArtifact>() {
                                        @Override
                                        void execute(IvyArtifact ivyArtifact) {
                                            ivyArtifact.setConf('shadow')
                                        }
                                    })
                                }
                            }
                        }
                    })
                }
            }
        }
    }
}
