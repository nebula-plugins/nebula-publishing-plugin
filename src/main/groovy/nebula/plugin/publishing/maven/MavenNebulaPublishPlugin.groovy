package nebula.plugin.publishing.maven

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.util.GradleVersion

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

        if (GradleVersion.current().baseVersion >= GradleVersion.version("4.8")) {
            project.afterEvaluate { p ->
                configurePublications(p)
            }
        } else {
            configurePublications(project)
        }
    }

    private void configurePublications(Project p) {
        p.publishing {
            publications {
                nebula(MavenPublication) {
                    if (p.ext.get(MAVEN_WAR)) {
                        from p.components.web
                    } else if (p.ext.get(MAVEN_JAR)) {
                        from p.components.java
                    }
                }
            }
        }
    }
}
