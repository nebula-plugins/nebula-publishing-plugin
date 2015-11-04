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
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.ivy.IvyPublication

class IvyExcludesPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply IvyBasePublishPlugin

        project.publishing {
            publications {
                nebulaIvy(IvyPublication) {
                    descriptor.withXml { XmlProvider xml ->
                        project.plugins.withType(JavaBasePlugin) {
                            def dependencies = xml.asNode()?.dependencies?.dependency

                            project.configurations.runtime.allDependencies
                                    .findAll { it instanceof ModuleDependency }
                                    .collect { it as ModuleDependency }
                                    .findAll { !it.excludeRules.isEmpty() }
                                    .each { md ->
                                        def dep = dependencies.find { it.@org == md.group && it.@name == md.name }
                                        md.excludeRules.each { rule ->
                                            def exclude = dep.appendNode('exclude')
                                            if (rule.group)
                                                exclude.@org = rule.group
                                            if (rule.module)
                                                exclude.@module = rule.module
                                        }
                                    }
                        }
                    }
                }
            }
        }
    }
}
