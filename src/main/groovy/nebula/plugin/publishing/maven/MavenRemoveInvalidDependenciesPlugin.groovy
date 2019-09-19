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
package nebula.plugin.publishing.maven

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.maven.MavenPublication

/**
 * Removes from descriptor dependencies that are invalid:
 * 1) No version available
 */
class MavenRemoveInvalidDependenciesPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.afterEvaluate {
            project.publishing {
                publications {
                    withType(MavenPublication) {
                        pom.withXml { XmlProvider xml ->
                            project.plugins.withType(JavaBasePlugin) {
                                def dependencies = xml.asNode()?.dependencies?.dependency
                                dependencies?.each { Node dep ->
                                    String version = dep.version.text()
                                    if(!version) {
                                       dep.parent().remove(dep)
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


