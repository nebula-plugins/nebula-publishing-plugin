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
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import static nebula.plugin.publishing.ManifestElementNameGenerator.*

/**
 * Adds {@link InfoBrokerPlugin} manifest entries to every Maven publication's POM properties.
 * Manifest keys are prefixed with {@code nebula_} and normalized for use as XML element names;
 * the manifest values are preserved.
 *
 * <p>The entries are supplied through a provider so contributors can register metadata after
 * a publication is configured. Adding them to {@link MavenPom#getProperties()} exposes the
 * metadata as modeled inputs to {@link org.gradle.api.publish.maven.tasks.GenerateMavenPom}.
 * On Gradle 9.8 and later, unchanged inputs can therefore leave POM generation up to date,
 * while changed manifest values invalidate those inputs.</p>
 *
 * <p>Other {@code pom.withXml} customizations can still disable up-to-date checks. This plugin
 * contributes to the existing properties map, preserving unrelated custom properties.</p>
 */
@CompileDynamic
class MavenManifestPlugin implements Plugin<Project> {
    /**
     * Applies the base publishing conventions and wires Info Broker metadata into existing
     * and subsequently added Maven publications.
     *
     * @param project the project whose publications should include manifest metadata
     */
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
                                pom.properties.putAll(project.provider {
                                    infoBroker.buildManifest().collectEntries { String key, String value ->
                                        [(elementName("nebula_$key")): value]
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
