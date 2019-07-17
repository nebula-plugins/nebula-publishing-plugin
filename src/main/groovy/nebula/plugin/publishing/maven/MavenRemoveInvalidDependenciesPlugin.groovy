package nebula.plugin.publishing.maven

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.maven.MavenPublication

class MavenRemoveInvalidDependenciesPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.afterEvaluate {
            project.publishing {
                publications {
                    withType(MavenPublication) {
                        pom.withXml { XmlProvider xml ->
                            project.plugins.withType(JavaBasePlugin) {
                                def dependencies = xml.asNode()?.dependencies?.dependency
                                dependencies?.each { Node dep ->
                                    String version = dep.version.text()
                                    if(!version) {
                                       dep.parent().remove(dep)
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


