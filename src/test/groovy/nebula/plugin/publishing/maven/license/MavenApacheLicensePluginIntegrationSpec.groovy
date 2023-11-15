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
package nebula.plugin.publishing.maven.license

import nebula.plugin.publishing.BaseIntegrationTestKitSpec

class MavenApacheLicensePluginIntegrationSpec extends BaseIntegrationTestKitSpec {
    def 'add license works for maven plugin publication'() {
        given:
        keepFiles = true
        buildFile << """
plugins {
    id 'com.netflix.nebula.maven-apache-license'
    id 'java-gradle-plugin'
    id 'com.gradle.plugin-publish' version "1.0.0"
    id 'maven-publish'
}

version = '0.1.0'
group = 'test.nebula'

gradlePlugin {
    plugins {
        samplePlugin {
            tags.set(['sample'])

            id = 'examplesample-plugin'
            displayName = 'Sample plugin'
            description = "Plugin that is a sample"
            implementationClass = 'SamplePlugin'
        }
    }
}

publishing {
    publications {
        // publish with a customized POM
        pluginMaven(MavenPublication) {
            pom {
                name = 'apachelicensepomtest pom!'
                inceptionYear = '2018'
            }
        }
    }
}
""".stripIndent()

        settingsFile << '''\
            rootProject.name = 'apachelicensepomtest'
        '''.stripIndent()


        def classesDir = new File(projectDir.path, 'src/main/java')
        classesDir.mkdirs()
        def samplePluginFile = new File(classesDir, 'SamplePlugin.groovy')
        samplePluginFile.createNewFile()
        samplePluginFile << """
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class SamplePlugin implements Plugin<Project> {
    Logger logger = Logging.getLogger(SamplePlugin);
    Project project
    void apply(Project project) {
        this.project = project
    }
}
"""

        when:
        runTasks('generatePomFileForPluginMavenPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, "build/publications/pluginMaven/pom-default.xml"))
        pom.licenses.license.name.text() == 'The Apache Software License, Version 2.0'
        pom.licenses.license.url.text() == 'http://www.apache.org/licenses/LICENSE-2.0.txt'
        pom.licenses.license.distribution.text() == 'repo'
    }
}
