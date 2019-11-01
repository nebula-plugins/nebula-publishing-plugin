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

import groovy.transform.CompileDynamic
import nebula.plugin.publishing.ivy.IvyBasePublishPlugin
import nebula.plugin.publishing.maven.MavenBasePublishPlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar

@CompileDynamic
class SourceJarPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.withType(JavaPlugin) {
            TaskProvider<Jar> sourceJarTask = project.tasks.register('sourceJar', Jar)
            sourceJarTask.configure(new Action<Jar>() {
                @Override
                void execute(Jar jar) {
                    jar.dependsOn project.tasks.named('classes')
                    jar.from project.sourceSets.main.allSource
                    jar.archiveClassifier.set 'sources'
                    jar.archiveExtension.set 'jar'
                    jar.group 'build'
                }
            })


            project.plugins.withType(MavenPublishPlugin) {
                project.plugins.apply(MavenBasePublishPlugin)

                project.publishing {
                    publications {
                        nebula(MavenPublication) {
                            artifact project.tasks.sourceJar
                        }
                    }
                }
            }

            project.plugins.withType(IvyPublishPlugin) {
                project.plugins.apply(IvyBasePublishPlugin)

                project.publishing {
                    publications {
                        nebulaIvy(IvyPublication) {
                            artifact(project.tasks.sourceJar) {
                                type 'sources'
                                conf 'sources'
                            }
                        }
                    }
                }
            }
        }
    }
}
