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
 * Replaces dynamic constraints with fixed constraints
 */
class MavenResolvedDependenciesPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(MavenBasePublishPlugin)

        project.publishing {
            publications {
                nebula(MavenPublication) {
                    pom.withXml { XmlProvider xml->
                        project.plugins.withType(JavaBasePlugin) {
                            def dependencies = xml.asNode()?.dependencies?.dependency
                            def dependencyMap = [:]

                            dependencyMap['runtime'] = project.configurations.runtime.incoming.resolutionResult.allDependencies
                            dependencyMap['test'] = project.configurations.testRuntime.incoming.resolutionResult.allDependencies - dependencyMap['runtime']
                            dependencies?.each { Node dep ->
                                def group = dep.groupId.text()
                                def name = dep.artifactId.text()
                                def scope = dep.scope.text()

                                if (scope == 'provided') {
                                    scope = 'runtime'
                                }

                                ResolvedDependencyResult resolved = dependencyMap[scope].find { r ->
                                    (r.requested instanceof ModuleComponentSelector) &&
                                            (r.requested.group == group) &&
                                            (r.requested.module == name)
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
}
