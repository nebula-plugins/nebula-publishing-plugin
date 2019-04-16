/*
 * Copyright 2015-2017 Netflix, Inc.
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

import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.maven.MavenPublication

/**
 * Replaces first order dependencies with the selected versions when publishing.
 */
class IvyResolvedDependenciesPlugin extends AbstractResolvedDependenciesPlugin {
    @Override
    void apply(Project project) {
        project.plugins.apply IvyBasePublishPlugin

        project.afterEvaluate {
            project.publishing {
                publications {
                    withType(IvyPublication) {

                        versionMapping {
                            allVariants {
                                fromResolutionResult()
                            }
                        }
                    }
                }
            }


        }

    }

    private VersionSelector parseSelector(String version) {
        def scheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator())
        def selector = scheme.parseSelector(version)
        selector
    }

    private void setVersionConstraint(VersionSelector selector, String version, Node dep, ModuleVersionIdentifier selected) {
        if (!(selector instanceof ExactVersionSelector)) {
            //requested dynamic version will be replaced by specific selected
            dep.@revConstraint = version
            dep.@rev = selected.version
        }
    }

    private void updateReplacedModules(ModuleVersionIdentifier mvid, String group, String name, Node dep) {
        if (mvid.group != group || mvid.name != name) {
            dep.@org = mvid.group
            dep.@name = mvid.name
            dep.@rev = mvid.version
        }
    }
}
