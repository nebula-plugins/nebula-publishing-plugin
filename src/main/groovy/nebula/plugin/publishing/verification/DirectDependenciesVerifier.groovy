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
package nebula.plugin.publishing.verification

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExcludeRule

class DirectDependenciesVerifier {
    static void verify(Project project) {
        project.configurations.forEach { Configuration configuration ->
            configuration.dependencies.forEach() { Dependency dependency ->
                ExcludeRule exclude
                try {
                    exclude = configuration.excludeRules.find {
                        it.group == dependency.group && it.module == dependency.name
                    }
                } catch (Exception e) {
                    // leave exclude null in case of unknown configuration
                }
                if(exclude) {
                    throw new GradleException("Direct dependency \"${dependency.group}:${dependency.name}\" is excluded, delete direct dependency or stop excluding it")
                }
            }
        }
    }
}
