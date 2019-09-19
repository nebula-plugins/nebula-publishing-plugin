package nebula.plugin.publishing.maven

import groovy.transform.CompileDynamic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.maven.MavenPublication

@CompileDynamic
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
                    if (! project.state.executed) {
                        //configuration when STABLE_PUBLISHING is enabled
                        project.afterEvaluate { p ->
                            configurePublication(it, p)
                        }
                    } else {
                        configurePublication(it, project)
                    }
                }
            }
        }
    }

    private void configurePublication(MavenPublication publication, Project p) {
        if (p.ext.get(MAVEN_WAR)) {
            publication.from p.components.web
        } else if (p.ext.get(MAVEN_JAR)) {
            publication.from p.components.java
        }
    }
}
