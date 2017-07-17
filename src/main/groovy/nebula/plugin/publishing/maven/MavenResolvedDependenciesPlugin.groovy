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

import nebula.plugin.publishing.ivy.AbstractResolvedDependenciesPlugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.maven.MavenPublication

/**
 * Replaces first order dependencies with the selected versions when publishing.
 */
class MavenResolvedDependenciesPlugin extends AbstractResolvedDependenciesPlugin {
    @Override
    void apply(Project project) {
        project.plugins.apply MavenBasePublishPlugin

        project.publishing {
            publications {
                withType(MavenPublication) {
                    pom.withXml { XmlProvider xml->
                        project.plugins.withType(JavaBasePlugin) {
                            def dependencies = xml.asNode()?.dependencies?.dependency
                            dependencies?.each { Node dep ->
                                String scope = dep.scope.text()
                                String group = dep.groupId.text()
                                String name = dep.artifactId.text()

                                ModuleVersionIdentifier mvid
                                if (scope == 'provided') {
                                    scope = 'runtime'
                                    mvid = selectedModuleVersion(project, scope, group, name)
                                    if (!mvid) {
                                        scope = 'compileOnly'
                                        mvid = selectedModuleVersion(project, scope, group, name)
                                    }
                                } else {
                                    mvid = selectedModuleVersion(project, scope, group, name)
                                }

                                if (!mvid) {
                                    return  // continue loop if a dependency is not found in dependencyMap
                                }

                                def versionNode = dep.version
                                if (!versionNode) {
                                    dep.appendNode('version')
                                }
                                dep.groupId[0].value = mvid.group
                                dep.artifactId[0].value = mvid.name
                                dep.version[0].value = mvid.version
                            }
                        }
                    }
                }
            }
        }
    }
}
