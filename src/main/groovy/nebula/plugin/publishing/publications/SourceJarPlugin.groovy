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

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskProvider

class SourceJarPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.withType(JavaPlugin).configureEach(new Action<JavaPlugin>() {
            @Override
            void execute(JavaPlugin javaPlugin) {
                project.extensions.configure(JavaPluginExtension, new Action<JavaPluginExtension>() {
                    @Override
                    void execute(JavaPluginExtension extension) {
                        extension.withSourcesJar()
                    }
                })

                TaskProvider sourceJarTask = project.tasks.register('sourceJar')
                sourceJarTask.configure(new Action<Task>() {
                    @Override
                    void execute(Task task) {
                        task.dependsOn(project.tasks.named('sourcesJar'))
                        task.doLast {
                            project.logger.info("sourceJar task has been replaced by sourcesJar")
                        }
                    }
                })
            }
        })
    }
}
