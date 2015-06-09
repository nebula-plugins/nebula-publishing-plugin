package nebula.plugin.publishing.maven

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

class ResolvedMavenPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(MavenPublishingPlugin)

        project.plugins.withType(MavenPublishPlugin) {
            project.publishing {
                publications {
                    nebula(MavenPublication) {
                        pom.withXml { XmlProvider xml ->
                            def dependencies = xml.asNode()?.dependencies?.dependency

                            def dependencyMap = [:]

                            project.logger.info(project.configurations.runtime.incoming.resolutionResult.allDependencies.toString())

                            dependencyMap['runtime'] = project.configurations.runtime.incoming.resolutionResult.allDependencies
                            dependencyMap['test'] = project.configurations.testRuntime.incoming.resolutionResult.allDependencies - dependencyMap['runtime']
                            dependencies?.each { Node dep ->
                                def group = dep.groupId.text()
                                def name = dep.artifactId.text()
                                def scope = dep.scope.text()

                                ResolvedDependencyResult resolved = dependencyMap[scope].find { r ->
                                    (r.requested.group == group) && (r.requested.module == name)
                                }

                                dep.version[0].value = resolved.selected.moduleVersion.version
                            }
                        }
                    }
                }
            }
        }
    }
}
