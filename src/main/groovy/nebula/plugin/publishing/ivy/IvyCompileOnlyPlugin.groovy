/*
 * Copyright 2017-2020 Netflix, Inc.
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
package nebula.plugin.publishing.ivy

import groovy.transform.Canonical
import groovy.transform.CompileDynamic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec
import org.gradle.api.publish.ivy.IvyPublication

@CompileDynamic
class IvyCompileOnlyPlugin implements Plugin<Project> {

    static enum DependenciesContent {
        dependency,
        exclude,
        override,
        conflict
    }

    void apply(Project project) {
        project.plugins.apply IvyBasePublishPlugin

        PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
        project.plugins.withType(JavaPlugin) { JavaPlugin javaBasePlugin ->
            project.afterEvaluate(new Action<Project>() {
                @Override
                void execute(Project p) {
                    def compileOnlyDependencies = project.configurations.compileOnly.incoming.dependencies.collect {
                        new Dependency(it.group, it.name, it.version)
                    }

                    publishing.publications(new Action<PublicationContainer>() {
                        @Override
                        void execute(PublicationContainer publications) {
                            publications.withType(IvyPublication) { IvyPublication publication ->
                                publication.descriptor(new Action<IvyModuleDescriptorSpec>() {
                                    @Override
                                    void execute(IvyModuleDescriptorSpec ivyModuleDescriptorSpec) {
                                        ivyModuleDescriptorSpec.withXml(new Action<XmlProvider>() {
                                            @Override
                                            void execute(XmlProvider xml) {
                                                def root = xml.asNode()
                                                def dependencies = compileOnlyDependencies
                                                if (dependencies.size() > 0) {
                                                    def confs = root.configurations ? root.configurations[0] : root.appendNode('configurations')
                                                    confs.appendNode('conf', [name: 'provided', visibility: 'public'])
                                                    def deps = root.dependencies ? root.dependencies[0] : root.appendNode('dependencies')
                                                    dependencies.each { dep ->
                                                        def newDep = deps.appendNode('dependency')
                                                        newDep.@org = dep.organisation
                                                        newDep.@name = dep.module
                                                        newDep.@rev = dep.version
                                                        newDep.@conf = 'provided'
                                                    }
                                                    deps.children().sort(true, {
                                                        DependenciesContent.valueOf(it.name()).ordinal()
                                                    })
                                                }
                                            }
                                        })
                                    }
                                })
                            }
                        }
                    })
                }
            })

        }
    }

    @Canonical
    private static class Dependency {
        String organisation
        String module
        String version
    }
}
