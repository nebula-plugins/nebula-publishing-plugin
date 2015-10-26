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
package nebula.plugin.publishing.ivy
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.ivy.IvyPublication

class IvyBasePublishPlugin implements Plugin<Project> {
    static final String IVY_WAR = 'nebulaPublish.ivy.war'
    static final String IVY_JAR = 'nebulaPublish.ivy.jar'

    @Override
    void apply(Project project) {
        project.plugins.apply org.gradle.api.publish.ivy.plugins.IvyPublishPlugin

        project.ext.set(IVY_WAR, false)
        project.ext.set(IVY_JAR, false)

        project.plugins.withType(WarPlugin) {
            project.ext.set(IVY_WAR, true)
        }

        project.plugins.withType(JavaPlugin) {
            project.ext.set(IVY_JAR, true)
        }

        project.publishing {
            publications {
                nebulaIvy(IvyPublication) {
                    if (project.ext.get(IVY_WAR)) {
                        from project.components.web
                    } else if (project.ext.get(IVY_JAR)) {
                        from project.components.java
                    }
                    descriptor.status = project.status
                    descriptor.withXml { XmlProvider xml ->
                        def root = xml.asNode()
                        def infoNode = root?.info
                        if (!infoNode) {
                            infoNode = root.appendNode('info')
                        } else {
                            infoNode = infoNode[0]
                        }
                        infoNode.appendNode('description', [:], project.description ?: '')
                    }
                }
            }
        }
    }
}
