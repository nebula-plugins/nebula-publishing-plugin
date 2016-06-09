package nebula.plugin.publishing.maven

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.maven.MavenPublication

class MavenNebulaPublishPlugin implements Plugin<Project> {
    static final String MAVEN_WAR = 'nebulaPublish.maven.war'
    static final String MAVEN_JAR = 'nebulaPublish.maven.jar'

    @Override
    void apply(Project project) {
        project.plugins.apply org.gradle.api.publish.maven.plugins.MavenPublishPlugin
        project.ext.set(MAVEN_WAR, false)
        project.ext.set(MAVEN_JAR, false)

        project.plugins.withType(WarPlugin) {
            project.ext.set(MAVEN_WAR, true)
        }

        project.plugins.withType(JavaPlugin) {
            project.ext.set(MAVEN_JAR, true)
        }

        project.publishing {
            publications {
                nebula(MavenPublication) {
                    if (project.ext.get(MAVEN_WAR)) {
                        from project.components.web
                    } else if (project.ext.get(MAVEN_JAR)) {
                        from project.components.java
                    }
                }
            }
        }
    }
}
