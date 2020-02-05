package nebula.plugin.publishing.maven

import groovy.transform.CompileDynamic
import nebula.plugin.publishing.publications.ShadowJarPublicationConfigurer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

@CompileDynamic
class MavenShadowPublishPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.afterEvaluate {
            ShadowJarPublicationConfigurer.configure(project, MavenPublication)
        }
    }
}
