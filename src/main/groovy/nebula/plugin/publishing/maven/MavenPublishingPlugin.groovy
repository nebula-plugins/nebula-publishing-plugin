package nebula.plugin.publishing.maven

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.maven.MavenPublication

class MavenPublishingPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply MavenBasePublishingPlugin

        setupPublishingForJavaOrWar(project)
        addProvidedConfToPom(project)
    }

    void setupPublishingForJavaOrWar(Project project) {
        project.afterEvaluate {
            project.publishing {
                publications {
                    nebula(MavenPublication) {
                        if (project.plugins.hasPlugin(WarPlugin)) {
                            from project.components.web
                            pom.withXml {
                                def dependenciesNode = asNode().appendNode('dependencies')

                                project.configurations.compile.allDependencies.each { Dependency dep ->
                                    def dependencyNode = dependenciesNode.appendNode('dependency')
                                    dependencyNode.with {
                                        appendNode('groupId', dep.group)
                                        appendNode('artifactId', dep.name)
                                        appendNode('version', dep.version)
                                        appendNode('scope', (isProvided(project, dep)) ? 'provided' : 'runtime')
                                    }
                                }
                            }
                        } else {
                            from project.components.java
                        }
                    }
                }
            }
        }
    }

    boolean isProvided(Project project, Dependency dep) {
        def isProvidedCompile = project.configurations?.providedCompile?.allDependencies?.
                find { provided -> provided.group == dep.group && provided.name == dep.name }
        if (isProvidedCompile) {
            return true
        }
        def isProvidedRuntime = project.configurations?.providedRuntime?.allDependencies?.
                find { provided -> provided.group == dep.group && provided.name == dep.name }

        isProvidedRuntime
    }

    void addProvidedConfToPom(Project project) {

    }
}
