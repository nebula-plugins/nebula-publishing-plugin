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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension

import javax.inject.Inject

@CompileDynamic
class SourceJarPlugin implements Plugin<Project> {

    private final ObjectFactory objectFactory

    @Inject
    SourceJarPlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory
    }

    @Override
    void apply(Project project) {
        project.plugins.withType(JavaPlugin) {
            JavaPluginExtension javaPluginExtension = project.extensions.getByType(JavaPluginExtension)
            javaPluginExtension.withSourcesJar()
        }
    }
}
