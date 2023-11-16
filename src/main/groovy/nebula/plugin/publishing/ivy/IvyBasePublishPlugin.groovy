/*
 * Copyright 2015-2020 Netflix, Inc.
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

import groovy.transform.CompileDynamic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyModuleDescriptorDescription
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec
import org.gradle.api.publish.ivy.IvyPublication

@CompileDynamic
class IvyBasePublishPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply org.gradle.api.publish.ivy.plugins.IvyPublishPlugin

        // CURRENT
        /*
            <conf name="default" visibility="public" extends="runtime"/>
            <conf visibility="public" name="runtime"/>
            <conf visibility="public" name="optional"/>
            <conf visibility="public" name="provided"/>
         */

        // EXPECTED
        /*
            <conf name="compile" visibility="public"/>
            <conf name="default" visibility="public" extends="runtime,master"/>
            <conf name="javadoc" visibility="public"/>
            <conf name="master" visibility="public"/>
            <conf name="resources" visibility="public"/>
            <conf name="runtime" visibility="public" extends="compile"/>
            <conf name="sources" visibility="public"/>
            <conf name="system" visibility="public"/>
            <conf name="test" visibility="public" extends="runtime"/>
            <conf name="optional" visibility="public"/>
            <conf name="provided" visibility="public"/>
         */

        PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
        project.afterEvaluate(new Action<Project>() {
            @Override
            void execute(Project p) {
                String status = p.status
                String description = p.description ?: ''
                publishing.publications(new Action<PublicationContainer>() {
                    @Override
                    void execute(PublicationContainer publications) {
                        publications.withType(IvyPublication) { IvyPublication publication ->
                            publication.descriptor.status = status
                            publication.descriptor.description(new Action<IvyModuleDescriptorDescription>() {
                                @Override
                                void execute(IvyModuleDescriptorDescription ivyModuleDescriptorDescription) {
                                    ivyModuleDescriptorDescription.text.set(description)
                                }
                            })
                            publication.descriptor(new Action<IvyModuleDescriptorSpec>() {
                                @Override
                                void execute(IvyModuleDescriptorSpec ivyModuleDescriptorSpec) {
                                    ivyModuleDescriptorSpec.withXml(new Action<XmlProvider>() {
                                        @Override
                                        void execute(XmlProvider xml) {
                                            def root = xml.asNode()
                                            def configurationsNode = root?.configurations
                                            if (!configurationsNode) {
                                                configurationsNode = root.appendNode('configurations')
                                            } else {
                                                configurationsNode = configurationsNode[0]
                                            }

                                            def minimalConfs = [
                                                    compile: [], default: ['runtime', 'master'], javadoc: [], master: [],
                                                    runtime: ['compile'], sources: [], test: ['runtime']
                                            ]

                                            minimalConfs.each { minimal ->
                                                def conf = configurationsNode.conf.find { it.@name == minimal.key }
                                                if (!conf) {
                                                    conf = configurationsNode.appendNode('conf')
                                                }
                                                conf.@name = minimal.key
                                                conf.@visibility = 'public'

                                                if (!minimal.value.empty)
                                                    conf.@extends = minimal.value.join(',')
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
