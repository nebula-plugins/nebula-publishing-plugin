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
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.maven.MavenPublication

class MavenBasePublishPlugin implements Plugin<Project> {
    static final String MAVEN_WAR = 'nebulaPublish.maven.war'
    static final String MAVEN_JAR = 'nebulaPublish.maven.jar'

    @Override
    void apply(Project project) {
        project.plugins.apply org.gradle.api.publish.maven.plugins.MavenPublishPlugin

        project.ext.set(MAVEN_WAR, false)
        project.ext.set(MAVEN_JAR, false)

        project.plugins.withType(WarPlugin) {
            project.ext.set(MAVEN_WAR, true)
        }

        project.plugins.withType(JavaPlugin) {
            project.ext.set(MAVEN_JAR, true)
        }

        project.publishing {
            publications {
                nebula(MavenPublication) {
                    if (project.ext.get(MAVEN_WAR)) {
                        from project.components.web
                    } else if (project.ext.get(MAVEN_JAR)) {
                        from project.components.java
                    }

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
