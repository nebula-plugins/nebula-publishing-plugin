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
package nebula.plugin.testkit

import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification

class IntegrationHelperSpec extends Specification {
    boolean keepFiles = false
    @Rule TestName testName = new TestName()
    File projectDir
    File buildFile
    File settingsFile

    def setup() {
        projectDir = new File("build/test/${this.class.canonicalName}/${testName.methodName.replaceAll(/\W+/, '-')}")
        if (projectDir.exists()) {
            projectDir.deleteDir()
        }
        projectDir.mkdirs()

        def pluginClasspathResource = this.class.classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
        }

        def pluginClasspath = pluginClasspathResource.readLines()
                .collect { it.replace('\\', '\\\\') } // escape backslashes in Windows paths
                .collect { "'$it'" }
                .join(", ")

        settingsFile = new File(projectDir, 'settings.gradle')
        buildFile = new File(projectDir, 'build.gradle')
        buildFile << """\
            buildscript {
                dependencies {
                    classpath files($pluginClasspath)
                }
            }
        """.stripIndent()
    }

    def cleanup() {
        if (!keepFiles) {
            projectDir.deleteDir()
        }
    }
}
