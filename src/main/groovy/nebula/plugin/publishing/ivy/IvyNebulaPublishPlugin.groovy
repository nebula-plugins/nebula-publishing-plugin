package nebula.plugin.publishing.ivy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.util.GradleVersion

class IvyNebulaPublishPlugin implements Plugin<Project> {
    static final String IVY_WAR = 'nebulaPublish.ivy.war'
    static final String IVY_JAR = 'nebulaPublish.ivy.jar'

    @Override
    void apply(Project project) {
        project.plugins.apply org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
        project.ext.set(IVY_WAR, false)
        project.ext.set(IVY_JAR, false)

        project.plugins.withType(WarPlugin) {
            project.ext.set(IVY_WAR, true)
        }

        project.plugins.withType(JavaPlugin) {
            project.ext.set(IVY_JAR, true)
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
                nebulaIvy(IvyPublication) {
                    if (p.ext.get(IVY_WAR)) {
                        from p.components.web
                    } else if (p.ext.get(IVY_JAR)) {
                        from p.components.java
                    }
                }
            }
        }
    }
}
