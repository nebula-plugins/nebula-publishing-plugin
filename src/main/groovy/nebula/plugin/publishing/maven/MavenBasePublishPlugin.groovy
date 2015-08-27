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

import nebula.plugin.publishing.PublishBasePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

class MavenBasePublishPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply PublishBasePlugin
        project.plugins.apply MavenPublishPlugin

        project.publishing {
            publications {
                nebula(MavenPublication) {
                    pom.withXml { XmlProvider xml ->
                        def root = xml.asNode()
                        root.appendNode('name', project.name)

                        // if there is no description block, Maven Central will
                        // not accept the artifact, but it is OK if it is blank
                        root.appendNode('description', project.description)
                    }
                }
            }
        }
    }
}
