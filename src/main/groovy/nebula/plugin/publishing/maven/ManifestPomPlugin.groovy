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

import nebula.plugin.info.InfoBrokerPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

class ManifestPomPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply MavenBasePublishingPlugin

        project.plugins.withType(InfoBrokerPlugin) { InfoBrokerPlugin infoBroker ->
            project.plugins.withType(MavenPublishPlugin) {
                project.publishing {
                    publications {
                        nebula(MavenPublication) {
                            pom.withXml { XmlProvider xml ->
                                Map<String, String> manifest = infoBroker.buildManifest()

                                def propertiesNode = xml.asNode()?.properties
                                if (!propertiesNode) {
                                    propertiesNode = xml.asNode().appendNode('properties')
                                }
                                //Node propertyNode = root.children().find { it.name == 'properties' }
                                manifest.each { key, value ->
                                    propertiesNode.appendNode(scrubElementName("nebula_$key"), value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static String scrubElementName(String name) {
        name.replaceAll(/\.|-/, '_').replaceAll(/\s/, '').trim()
    }
}
