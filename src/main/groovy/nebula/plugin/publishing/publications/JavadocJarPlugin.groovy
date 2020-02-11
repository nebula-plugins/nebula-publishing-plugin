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
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.jvm.tasks.Jar

@CompileDynamic
class JavadocJarPlugin implements Plugin<Project> {

    private static final String JAVADOC_JAR_TASK = 'javadocJar'

    @Override
    void apply(Project project) {
        project.afterEvaluate {
            project.plugins.withType(JavaPlugin){
                if(javadocJarAlreadyExists(project)) {
                    configureExistingJavadocTask(project)
                } else {
                    configureJavadocTask(project)
                }
            }
        }
    }

    private boolean javadocJarAlreadyExists(Project project) {
        return project.tasks.getNames().contains(JAVADOC_JAR_TASK)
    }

    private void configureJavadocTask(Project project) {
        JavaPluginExtension javaPluginExtension = project.extensions.getByType(JavaPluginExtension)
        javaPluginExtension.withJavadocJar()
    }

    private void configureExistingJavadocTask(Project project) {
        project.tasks.named(JAVADOC_JAR_TASK, Jar).configure(new Action<Jar>() {
            @Override
            void execute(Jar task) {
                task.dependsOn project.tasks.named('javadoc')
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
                                    type 'javadoc'
                                    conf 'javadoc'
                                }
                            }
                        }
                    }
                }
            }
        })
    }
}
