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
package nebula.plugin.publishing.publications.deprecated

import nebula.plugin.publishing.publications.TestJarPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

@Deprecated
class DeprecatedTestJarPlugin implements Plugin<Project> {
    static Logger logger = Logging.getLogger(DeprecatedTestJarPlugin)

    @Deprecated
    @Override
    void apply(Project project) {
        logger.warn 'Please begin using `apply plugin: \'nebula.test-jar\'` instead of `apply plugin: \'nebula-test-jar\'`'
        project.plugins.apply TestJarPlugin
    }
}
