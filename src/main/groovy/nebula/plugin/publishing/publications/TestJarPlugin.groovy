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
package nebula.plugin.publishing.publications

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar

class TestJarPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.withType(JavaPlugin) { // needed for source sets
            project.tasks.create('testJar', Jar) {
                dependsOn project.tasks.getByName('testClasses')
                classifier = 'tests'
                extension = 'jar'
                from project.sourceSets.test.output
                group 'build'
            }

            project.plugins.withType(MavenPublishPlugin) {
                project.publishing {
                    publications {
                        nebula(MavenPublication) {
                            artifact project.tasks.testJar

                            pom.withXml { XmlProvider xml ->
                                def root = xml.asNode()
                                def dependencies = root.dependencies ? root.dependencies[0] : root.appendNode('dependencies')

                                [project.configurations.testCompile, project.configurations.testRuntime].each {
                                    it.dependencies.each { dep ->
                                        dependencies.appendNode('dependency').with {
                                            appendNode('groupId', dep.group)
                                            appendNode('artifactId', dep.name)
                                            appendNode('version', dep.version)
                                            appendNode('scope', 'test')
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
}
