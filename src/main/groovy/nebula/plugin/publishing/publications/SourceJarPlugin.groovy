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
package nebula.plugin.publishing.publications

import groovy.transform.CompileDynamic
import nebula.plugin.publishing.ivy.IvyBasePublishPlugin
import nebula.plugin.publishing.maven.MavenBasePublishPlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

@CompileDynamic
class SourceJarPlugin implements Plugin<Project> {

    private static final String SOURCES_JAR_TASK = 'sourcesJar'

    @Override
    void apply(Project project) {
        project.afterEvaluate {
            project.plugins.withType(JavaPlugin){
                if(sourcesJarAlreadyExists(project)) {
                    configureExistingSourcesJarTask(project)
                } else {
                    configureSourcesJarTask(project)
                }
            }
        }

        TaskProvider sourceJarTask = project.tasks.register('sourceJar')
        sourceJarTask.configure(new Action<Task>() {
            @Override
            void execute(Task task) {
                Task sourcesJar = project.tasks.findByName('sourcesJar')
                if(sourcesJar) {
                    task.dependsOn(project.tasks.named('sourcesJar'))
                }
                task.doLast {
                    project.logger.info("sourceJar task has been replaced by sourcesJar")
                }
            }
        })
    }

    private boolean sourcesJarAlreadyExists(Project project) {
        return project.tasks.getNames().contains(SOURCES_JAR_TASK)
    }

    private void configureSourcesJarTask(Project project) {
        JavaPluginExtension javaPluginExtension = project.extensions.getByType(JavaPluginExtension)
        javaPluginExtension.withSourcesJar()
    }

    private void configureExistingSourcesJarTask(Project project) {
        project.tasks.named(SOURCES_JAR_TASK, Jar).configure(new Action<Jar>() {
            @Override
            void execute(Jar task) {
                task.dependsOn project.tasks.named('classes')
                project.plugins.withType(MavenPublishPlugin) {
                    project.plugins.apply(MavenBasePublishPlugin)

                    project.publishing {
                        publications {
                            nebula(MavenPublication) {
                                artifact task
                            }
                        }
                    }
                }

                project.plugins.withType(IvyPublishPlugin) {
                    project.plugins.apply(IvyBasePublishPlugin)

                    project.publishing {
                        publications {
                            nebulaIvy(IvyPublication) {
                                artifact(task) {
                                    type 'sources'
                                    conf 'sources'
                                }
                            }
                        }
                    }
                }
            }
        })
    }
}
