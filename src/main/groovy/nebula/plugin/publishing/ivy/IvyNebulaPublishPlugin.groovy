package nebula.plugin.publishing.ivy

import groovy.transform.CompileDynamic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.ivy.IvyPublication

@CompileDynamic
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

        project.afterEvaluate { p ->
            def component = getComponent(p)
            project.publishing {
                publications {
                    nebulaIvy(IvyPublication) { publication ->
                        if(component) {
                            publication.from component
                        }
                    }
                }
            }
        }
    }

    private getComponent(Project p) {
        if(p.ext.get(IVY_WAR)) {
            return p.components.web
        } else if(p.ext.get(IVY_JAR)) {
            return p.components.java
        } else {
            return null
        }
    }
}
