package nebula.plugin.publishing.ivy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.ivy.IvyPublication

class IvyRemovePlatformDependenciesPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.publishing {
            publications {
                withType(IvyPublication) {
                    descriptor.withXml { XmlProvider xml ->
                        xml.asNode().dependencies.dependency.findAll() { Node dep ->
                            String scope = dep.@conf
                            String group = dep.@org
                            String name = dep.@name

                            if (scope == 'compile->default') {
                                scope = 'compile'
                            }

                            if (scope == 'provided->default' || scope == 'runtime->default') {
                                scope = 'runtime'
                            }

                            if (scope == 'test->default') {
                                scope = 'test'
                            }

                            if (scope.contains('->')) {
                                scope = scope.split('->')[0]
                            }

                            boolean isPlatformDependency = PlatformDependencyVerifier.isPlatformDependency(project, scope, group, name)

                            if(isPlatformDependency) {
                                dep.parent().remove(dep)
                            }
                        }
                    }
                }
            }
        }
    }
}
