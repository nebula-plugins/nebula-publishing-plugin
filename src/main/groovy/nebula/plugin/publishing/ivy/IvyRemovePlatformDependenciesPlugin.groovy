/*
 * Copyright 2019 Netflix, Inc.
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
import org.gradle.api.publish.ivy.IvyPublication


/**
 * Removes from descriptor dependencies that have category status of platform or enhanced-platform
 */
class IvyRemovePlatformDependenciesPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.publishing {
            publications {
                withType(IvyPublication) {
                    descriptor.withXml { XmlProvider xml ->
                        xml.asNode().dependencies.dependency.findAll() { Node dep ->
                            String scope = dep.@conf
                            String group = dep.@org
                            String name = dep.@name

                            if (scope == 'compile->default') {
                                scope = 'compile'
                            }

                            if (scope == 'provided->default' || scope == 'runtime->default') {
                                scope = 'runtime'
                            }

                            if (scope == 'test->default') {
                                scope = 'test'
                            }

                            if (scope.contains('->')) {
                                scope = scope.split('->')[0]
                            }

                            boolean isPlatformDependency = PlatformDependencyVerifier.isPlatformDependency(project, scope, group, name)

                            if(isPlatformDependency) {
                                dep.parent().remove(dep)
                            }
                        }
                    }
                }
            }
        }
    }
}
