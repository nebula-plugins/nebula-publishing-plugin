package nebula.plugin.publishing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

class PublishingBasePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.ext {
            nebulaPublish = [] as List<Closure>
        }
    }
}
