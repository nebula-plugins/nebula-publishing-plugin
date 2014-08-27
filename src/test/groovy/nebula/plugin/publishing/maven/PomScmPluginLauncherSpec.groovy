/*
 * Copyright 2014 Netflix, Inc.
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
import nebula.test.IntegrationSpec

/**
 * Info Plugin will troll of SCM values
 */
class PomScmPluginLauncherSpec extends IntegrationSpec {

    def pomLocation = 'build/publications/mavenNebula/pom-default.xml'

    def 'look in pom'() {

        buildFile << """
            apply plugin: 'nebula-publishing'
            apply plugin: 'info'
            """.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForMavenNebulaPublication')

        then: 'pom exists'
        fileExists(pomLocation)
        def pomFile = new File(projectDir, pomLocation)
        def pom = new XmlSlurper().parse(pomFile)

        then: 'developer section is filled in'
        pom.scm.url.text() == 'scm:git@github.com:nebula-plugins/nebula-publishing-plugin.git'
        pom.scm.connection.text() == 'scm:git@github.com:nebula-plugins/nebula-publishing-plugin.git'
        pom.url.text() == 'https://github.com/nebula-plugins/nebula-publishing-plugin'
    }
}
