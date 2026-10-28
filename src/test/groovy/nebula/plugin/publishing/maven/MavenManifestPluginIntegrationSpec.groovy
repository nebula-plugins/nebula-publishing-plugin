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
package nebula.plugin.publishing.maven

import groovy.xml.XmlSlurper
import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import org.gradle.testkit.runner.TaskOutcome

class MavenManifestPluginIntegrationSpec extends BaseIntegrationTestKitSpec {
    def setup() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.maven-manifest'
                id 'com.netflix.nebula.maven-nebula-publish'
            }

            version = '0.1.0'
            group = 'test.nebula'
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'manifestpomtest'
        '''.stripIndent()
    }

    def 'manifest created'() {
        buildFile << '''\
            apply plugin: 'java'
            apply plugin: 'com.netflix.nebula.info'
        '''

        when:
        runTasks('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.properties.children().size() > 1

        and:
        String change = pom.properties.'nebula_Change'
        String fullChange = pom.properties.'nebula_Full_Change'
        pom.properties.'nebula_Implementation_Version' == '0.1.0'
        pom.properties.'nebula_Implementation_Title' == 'test.nebula#manifestpomtest;0.1.0'
        fullChange
        fullChange.startsWith(change)
        fullChange.size() > change.size()
    }

    def 'unchanged modeled manifest properties keep all publications up to date on Gradle 9.8'() {
        given:
        gradleVersion = '9.8.2'
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'com.netflix.nebula.info-broker'

            publishing.publications {
                custom(MavenPublication) {
                    from components.java
                    artifactId = 'custom-artifact'
                }
                withType(MavenPublication).configureEach {
                    pom.properties.put('custom', 'preserved')
                }
            }

            // Nebula's origin entries may be added after the publication's POM is configured.
            def broker = plugins.getPlugin(nebula.plugin.info.InfoBrokerPlugin)
            broker.add('Gradle-Project-Path', project.path)
            broker.add('Gradle-Project-Directory', '.')
            broker.add('Entry.with spaces', { providers.gradleProperty('manifestValue').getOrElse('one') })
        '''
        def taskPaths = ['generatePomFileForNebulaPublication', 'generatePomFileForCustomPublication']
        runTasks(*(taskPaths + ['--configuration-cache-problems=fail']))

        when:
        def unchanged = runTasks(*(taskPaths + ['--configuration-cache-problems=fail']))

        then:
        taskPaths.each { taskPath ->
            assert unchanged.task(":$taskPath").outcome == TaskOutcome.UP_TO_DATE
        }
        unchanged.output.contains('Reusing configuration cache.')
        ['nebula', 'custom'].each { publication ->
            def pom = new XmlSlurper().parse(new File(projectDir, "build/publications/$publication/pom-default.xml"))
            assert pom.properties.'nebula_Gradle_Project_Path'*.text() == [':']
            assert pom.properties.'nebula_Gradle_Project_Directory'*.text() == ['.']
            assert pom.properties.'nebula_Entry_withspaces'*.text() == ['one']
            assert pom.properties.custom.text() == 'preserved'
        }

        when:
        def changed = runTasks(*(taskPaths + ['-PmanifestValue=two', '--configuration-cache-problems=fail']))

        then:
        taskPaths.each { taskPath ->
            assert changed.task(":$taskPath").outcome == TaskOutcome.SUCCESS
        }
        ['nebula', 'custom'].each { publication ->
            def pom = new XmlSlurper().parse(new File(projectDir, "build/publications/$publication/pom-default.xml"))
            assert pom.properties.'nebula_Gradle_Project_Path'*.text() == [':']
            assert pom.properties.'nebula_Gradle_Project_Directory'*.text() == ['.']
            assert pom.properties.'nebula_Entry_withspaces'*.text() == ['two']
            assert pom.properties.custom.text() == 'preserved'
        }
    }
}
