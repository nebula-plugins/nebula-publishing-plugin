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
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc

class JavadocJarPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.withType(JavaPlugin) {
            Javadoc javadocTask = (Javadoc) project.tasks.getByName('javadoc')
            project.tasks.create('javadocJar', Jar) {
                dependsOn javadocTask
                from javadocTask.destinationDir
                classifier 'javadoc'
                extension 'jar'
                group 'build'
            }

            project.plugins.withType(org.gradle.api.publish.maven.plugins.MavenPublishPlugin) {
                project.publishing {
                    publications {
                        nebula(MavenPublication) {
                            artifact project.tasks.javadocJar
                        }
                    }
                }
            }

            project.plugins.withType(org.gradle.api.publish.ivy.plugins.IvyPublishPlugin) {
                project.publishing {
                    publications {
                        nebulaIvy(IvyPublication) {
                            artifact project.tasks.javadocJar
                        }
                    }
                }
            }
        }
    }
}
