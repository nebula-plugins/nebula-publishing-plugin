package nebula.plugin.publishing.maven

import nebula.plugin.publishing.PublishingBasePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

class MavenBasePublishingPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply PublishingBasePlugin
        project.plugins.apply MavenPublishPlugin

        project.publishing {
            publications {
                nebula(MavenPublication) {

                }
            }
        }
    }
}
