package nebula.plugin.publishing.publications

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar

class SourceJarPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.withType(JavaPlugin) {
            project.tasks.create('sourceJar', Jar) {
                dependsOn project.tasks.getByName('classes')
                from project.sourceSets.main.allSource
                classifier 'sources'
                extension 'jar'
                group 'build'
            }

            project.plugins.withType(MavenPublishPlugin) {
                project.publishing {
                    publications {
                        nebula(MavenPublication) {
                            artifact project.tasks.sourceJar
                        }
                    }
                }
            }
        }
    }
}
