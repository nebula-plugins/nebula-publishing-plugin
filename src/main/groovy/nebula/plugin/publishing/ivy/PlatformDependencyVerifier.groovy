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

import groovy.transform.Memoized
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.attributes.Category

/**
 * Verifies if a dependency has platform or enhanced-platform category attribute
 */
class PlatformDependencyVerifier {

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
        return platformDependencies.find { requested -> requested.group == group && requested.name == name
        }
    }

    @Memoized
    static Map<String, Set<? extends ModuleIdentifier>> findPlatformDependencies(Project project) {
        ConfigurationContainer configurations = project.configurations
        Map<String, Set<? extends ModuleIdentifier>> dependencyMap = [:]
        dependencyMap['runtime'] = platformDependencies(configurations.runtimeClasspath)
        dependencyMap['compile'] = platformDependencies(configurations.compileClasspath)
        dependencyMap['compileOnly'] = platformDependencies(configurations.compileOnly)
        dependencyMap['test'] = platformDependencies(configurations.testRuntimeClasspath)
        dependencyMap
    }

    static Set<? extends ModuleIdentifier> platformDependencies(Configuration configuration) {
        return configuration.incoming.resolutionResult.allDependencies.requested.findAll {
            it.attributes.keySet().name.contains(Category.CATEGORY_ATTRIBUTE.name) && it.attributes.findEntry(Category.CATEGORY_ATTRIBUTE.name).get() in [Category.REGULAR_PLATFORM, Category.ENFORCED_PLATFORM]
        }?.moduleIdentifier
    }
}
