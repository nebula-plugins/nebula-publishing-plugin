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
package nebula.plugin.publishing.maven

import groovy.transform.CompileDynamic
import nebula.plugin.info.InfoBrokerPlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import static nebula.plugin.publishing.ManifestElementNameGenerator.*

@CompileDynamic
class MavenManifestPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply MavenBasePublishPlugin

        try {
            Class.forName('nebula.plugin.info.InfoBrokerPlugin')
        } catch (Throwable ex) {
            project.logger.info('Skipping adding extra manifest elements from the info plugin as it has not been applied')
            return
        }

        project.plugins.withType(InfoBrokerPlugin) { InfoBrokerPlugin infoBroker ->
            PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
            publishing.publications(new Action<PublicationContainer>() {
                @Override
                void execute(PublicationContainer publications) {
                    publications.withType(MavenPublication) { MavenPublication publication ->
                        publication.pom(new Action<MavenPom>() {
                            @Override
                            void execute(MavenPom pom) {
                                pom.withXml(new Action<XmlProvider>() {
                                    @Override
                                    void execute(XmlProvider xml) {
                                        def propertiesNode = xml.asNode()?.properties
                                        if (!propertiesNode) {
                                            propertiesNode = xml.asNode().appendNode('properties')
                                        }
                                        infoBroker.buildManifest().each { String key, String value ->
                                            propertiesNode.appendNode(elementName("nebula_$key"), value)
                                        }
                                    }
                                })
                            }
                        })
                    }
                }
            })
        }
    }
}
