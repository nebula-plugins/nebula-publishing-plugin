package nebula.plugin.publishing.ivy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.ivy.IvyPublication

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

        project.publishing {
            publications {
                nebulaIvy(IvyPublication) {
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

    private void configurePublication(IvyPublication publication, Project p) {
        if (p.ext.get(IVY_WAR)) {
            publication.from p.components.web
        } else if (p.ext.get(IVY_JAR)) {
            publication.from p.components.java
        }
    }
}
