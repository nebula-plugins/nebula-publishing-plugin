package nebula.plugin.publishing.ivy

import groovy.transform.CompileDynamic
import nebula.plugin.publishing.publications.ShadowJarPublicationConfigurer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.ivy.IvyPublication

@CompileDynamic
class IvyShadowPublishPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.afterEvaluate {
            ShadowJarPublicationConfigurer.configure(project, IvyPublication)
        }
    }
}
