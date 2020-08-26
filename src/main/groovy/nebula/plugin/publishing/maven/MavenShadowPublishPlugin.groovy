package nebula.plugin.publishing.maven

import groovy.transform.CompileDynamic
import nebula.plugin.publishing.publications.ShadowJarPublicationConfigurer
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

@CompileDynamic
class MavenShadowPublishPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.afterEvaluate {
            project.plugins.withId('com.github.johnrengelman.shadow') {
                Task shadowJarTask = project.tasks.findByName('shadowJar')
                boolean jarTaskEnabled = project.tasks.findByName('jar').enabled
                if (!jarTaskEnabled) {
                    ShadowJarPublicationConfigurer.configureForJarDisabled(project)
                } else {
                    //presence of this configurations means that shadow jar plugin is version 6.+
                    //all the configuration below is done for us there and we can skip
                    if (project.configurations.findByName("shadowRuntimeElements") == null) {
                        PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
                        publishing.publications(new Action<PublicationContainer>() {
                            @Override
                            void execute(PublicationContainer publications) {
                                if (shadowJarTask) {
                                    publications.withType(MavenPublication) { MavenPublication publication ->
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
}
