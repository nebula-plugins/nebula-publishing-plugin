/*
 * Copyright 2019-2020 Netflix, Inc.
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

import groovy.transform.CompileDynamic
import groovy.transform.Memoized
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector

/**
 * Verifies if a dependency has platform or enhanced-platform category attribute
 */
@CompileDynamic
class PlatformDependencyVerifier {

    private static final String CATEGORY_ATTRIBUTE = "org.gradle.category"
    private static final String REGULAR_PLATFORM = "platform"
    private static final  String ENFORCED_PLATFORM = "enforced-platform"

    static boolean isPlatformDependency(Project project, String scope, String group, String name) {
        Boolean result = checkIfPlatformDependency(project, scope, group, name)
        Map<String, String> scoping = [compile: 'runtime', provided: 'compileOnly']
        if (!result && scoping[scope]) {
            result = checkIfPlatformDependency(project, scoping[scope], group, name)
        }
        result
    }

    private static boolean checkIfPlatformDependency(Project project, String scope, String group, String name) {
        def platformDependencies = findPlatformDependencies(project)[scope]
        return platformDependencies.find { ComponentSelector componentSelector ->
            if(componentSelector instanceof ModuleComponentSelector) {
                return componentSelector.moduleIdentifier.group == group && componentSelector.moduleIdentifier.name == name
            } else if(componentSelector instanceof ProjectComponentSelector) {
                return componentSelector.projectName == name
            }
        }
    }

    @Memoized
    static Map<String, Set<? extends ComponentSelector>> findPlatformDependencies(Project project) {
        ConfigurationContainer configurations = project.configurations
        Map<String, Set<? extends ComponentSelector>> dependencyMap = [:]
        dependencyMap['runtime'] = platformDependencies(configurations.runtimeClasspath)
        dependencyMap['compile'] = platformDependencies(configurations.compileClasspath)
        dependencyMap['test'] = platformDependencies(configurations.testRuntimeClasspath)
        dependencyMap
    }

    static Set<? extends ComponentSelector> platformDependencies(Configuration configuration) {
        return configuration.incoming.resolutionResult.allDependencies.requested.findAll {
           it.attributes.keySet().name.contains(CATEGORY_ATTRIBUTE) && it.attributes.findEntry(CATEGORY_ATTRIBUTE).get() in [REGULAR_PLATFORM, ENFORCED_PLATFORM]
        }
    }
}
