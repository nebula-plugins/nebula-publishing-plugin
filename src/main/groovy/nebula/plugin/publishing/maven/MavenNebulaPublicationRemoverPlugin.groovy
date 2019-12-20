package nebula.plugin.publishing.maven

import groovy.transform.CompileDynamic
import org.gradle.api.Plugin
import org.gradle.api.Project

@CompileDynamic
class MavenNebulaPublicationRemoverPlugin  implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.withType(MavenNebulaPublishPlugin) {
            project.plugins.withId("org.springframework.cloud.contract") {
                project.afterEvaluate {
                    project.publishing.publications.remove project.publishing.publications.find { it.name == 'nebula' }
                    project.tasks.findAll { it.name.startsWith('publishNebula') }.each {
                        it.enabled = false
                    }
                }
            }
        }
    }
}
