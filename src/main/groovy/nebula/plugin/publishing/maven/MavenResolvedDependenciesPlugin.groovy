/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.publishing.maven

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.maven.MavenPublication

/**
 * Replaces first order dependencies with the selected versions when publishing.
 */
class MavenResolvedDependenciesPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply MavenBasePublishPlugin

        project.publishing {
            publications {
                withType(MavenPublication) {
                    pom.withXml { XmlProvider xml->
                        project.plugins.withType(JavaBasePlugin) {
                            def dependencies = xml.asNode()?.dependencies?.dependency
                            def dependencyMap = [:]
                            dependencyMap['compile'] = project.configurations.compileClasspath.incoming.resolutionResult.allDependencies
                            dependencyMap['runtime'] = project.configurations.runtimeClasspath.incoming.resolutionResult.allDependencies
                            dependencyMap['compileOnly'] = project.configurations.compileOnly.incoming.resolutionResult.allDependencies
                            dependencyMap['test'] = project.configurations.testRuntime.incoming.resolutionResult.allDependencies - dependencyMap['compile'] - dependencyMap['runtime']

                            dependencies?.each { Node dep ->
                                String group = dep.groupId.text()
                                String name = dep.artifactId.text()
                                String scope = dep.scope.text()

                                ResolvedDependencyResult resolved
                                if (scope == 'provided') {
                                    scope = 'runtime'
                                    resolved = resolvedDep(dependencyMap, scope, group, name)
                                    if (!resolved) {
                                        scope = 'compileOnly'
                                        resolved = resolvedDep(dependencyMap, scope, group, name)
                                    }
                                } else {
                                    resolved = resolvedDep(dependencyMap, scope, group, name)
                                }

                                if (!resolved) {
                                    return  // continue loop if a dependency is not found in dependencyMap
                                }

                                def versionNode = dep.version
                                if (!versionNode) {
                                    dep.appendNode('version')
                                }
                                def moduleVersion = resolved.selected.moduleVersion
                                dep.groupId[0].value = moduleVersion.group
                                dep.artifactId[0].value = moduleVersion.name
                                dep.version[0].value = moduleVersion.version
                            }
                        }
                    }
                }
            }
        }
    }

    ResolvedDependencyResult resolvedDep(Map dependencyMap, String scope, String group, String name) {
        dependencyMap[scope].find { r ->
            (r.requested instanceof ModuleComponentSelector) &&
                    (r.requested.group == group) &&
                    (r.requested.module == name)
        }
    }
}
