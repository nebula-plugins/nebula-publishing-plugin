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

import nebula.plugin.publishing.PublicationBase
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar

class JavadocJarPlugin implements PublicationBase {
    @Override
    void apply(Project project) {
        project.plugins.withType(JavaPlugin) {

            def javadocJar = addTaskLocal(project, [name       : TASK_NAME_JAVADOC_JAR,
                                                    description: TASK_DESC_JAVADOC_JAR,
                                                    group      : CORE_GROUP_BUILD,
                                                    type       : Jar])

            buildConfigureTask(project, javadocJar, ARCHIVE_CLASSIFIER_JAVADOC, CORE_TASK_JAVADOC, EXTENSION_JAR)
        }
    }
}
