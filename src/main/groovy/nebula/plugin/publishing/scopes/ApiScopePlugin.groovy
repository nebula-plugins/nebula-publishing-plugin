/*
 * Copyright 2016 Netflix, Inc.
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
package nebula.plugin.publishing.scopes

import groovy.transform.CompileDynamic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin


@Deprecated
@CompileDynamic
class ApiScopePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.withType(JavaBasePlugin) {
            def compileApi = project.configurations.create('compileApi')
            project.configurations.compile.extendsFrom compileApi
        }

        project.plugins.withType(MavenPublishPlugin) {
            project.publishing {
                publications {
                    withType(MavenPublication) {
                        pom.withXml { XmlProvider xml ->
                            project.plugins.withType(JavaBasePlugin) {
                                def dependencies = xml.asNode()?.dependencies?.dependency
                                def apiDeps = project.configurations.compileApi.incoming.dependencies
                                dependencies?.each { Node dep ->
                                    def group = dep.groupId.text()
                                    def name = dep.artifactId.text()

                                    if (apiDeps.find { Dependency d -> d.group == group && d.name == name }) {
                                        dep.scope[0].setValue('compile')
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        project.plugins.withType(IvyPublishPlugin) {
            project.publishing {
                publications {
                    withType(IvyPublication) {
                        descriptor.withXml { XmlProvider xml ->
                            project.plugins.withType(JavaBasePlugin) {
                                def root = xml.asNode()
                                def dependencies = root?.dependencies?.dependency
                                def apiDeps = project.configurations.compileApi.incoming.dependencies
                                dependencies?.each { Node dep ->
                                    def org = dep.@org
                                    def name = dep.@name

                                    if (apiDeps.find { Dependency d -> d.group == org && d.name == name }) {
                                        def configurationsNode = root?.configurations
                                        if(!configurationsNode) {
                                            configurationsNode = root.appendNode('configurations')
                                        }
                                        else {
                                            configurationsNode = configurationsNode[0]
                                        }
                                        def conf = configurationsNode.conf.find { it.@name == 'compile' }
                                        if(!conf) {
                                            conf = configurationsNode.appendNode('conf')
                                            conf.@name = 'compile'
                                            conf.@visibility = 'public'

                                            def runtime = configurationsNode.conf.find { it.@name == 'runtime' }
                                            runtime.@extends = 'compile'
                                        }
                                        dep.@conf = 'compile->default'
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
