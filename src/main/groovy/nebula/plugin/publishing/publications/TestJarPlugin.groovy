package nebula.plugin.publishing.publications

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar

class TestJarPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.withType(JavaPlugin) { // needed for source sets
            project.tasks.create('testJar', Jar) {
                dependsOn project.tasks.getByName('testClasses')
                classifier = 'tests'
                extension = 'jar'
                from project.sourceSets.test.output
                group 'build'
            }

            project.plugins.withType(MavenPublishPlugin) {
                project.publishing {
                    publications {
                        nebula(MavenPublication) {
                            artifact project.tasks.testJar
                        }
                    }
                }
            }
        }
    }
}
