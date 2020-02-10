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

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import spock.lang.Unroll

class MavenBasePublishPluginIntegrationSpec extends IntegrationTestKitSpec {
    File publishDir

    def setup() {
        buildFile << """\
            plugins {
                id 'nebula.maven-base-publish'
                id 'nebula.maven-nebula-publish'
            }

            version = '0.1.0'
            group = 'test.nebula'

            publishing {
                repositories {
                    maven {
                        name = 'testLocal'
                        url = 'testrepo'
                    }
                }
            }
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'maventest'
        '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test/nebula/maventest/0.1.0')
    }

    def 'name appears in pom'() {
        when:
        runTasks('generatePomFileForNebulaPublication')

        then:
        def pomFile = new File(projectDir, 'build/publications/nebula/pom-default.xml')
        def pom = new XmlSlurper().parse(pomFile)

        pom.name == 'maventest'
    }

    def 'description appears in pom'() {
        given:
        buildFile << '''\
            description = 'Test description'
        '''.stripIndent()

        when:
        runTasks('generatePomFileForNebulaPublication')

        then:
        def pomFile = new File(projectDir, 'build/publications/nebula/pom-default.xml')
        def pom = new XmlSlurper().parse(pomFile)

        pom.description == 'Test description'
    }

    def 'creates a jar publication'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.jar').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
    }

    def 'creates a jar publication for scala projects'() {
        buildFile << '''\
            apply plugin: 'scala'
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.jar').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
    }

    def 'creates a jar publication for groovy projects'() {
        buildFile << '''\
            apply plugin: 'groovy'
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.jar').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
    }

    def 'creates a war publication'() {
        buildFile << '''\
            apply plugin: 'war'
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.war').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
    }

    def 'creates a war publication in presence of java plugin'() {
        buildFile << '''\
            apply plugin: 'java'
            apply plugin: 'war'
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.war').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
        !new File(publishDir, 'maventest-0.1.0.jar').exists()
    }

    def 'creates a war publication in presence of java plugin no matter the order'() {
        buildFile << '''\
            apply plugin: 'war'
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.war').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
        !new File(publishDir, 'maventest-0.1.0.jar').exists()
    }

    def 'verify pom is correct'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def resultPom = new File(publishDir, 'maventest-0.1.0.pom').text
        resultPom.contains("<groupId>test.nebula</groupId>")
        resultPom.contains("<artifactId>maventest</artifactId>")
        resultPom.contains("<version>0.1.0</version>")
        resultPom.contains("<name>maventest</name>")
    }

    def 'verify pom contains compile dependencies'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:a:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java-library'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                api 'testjava:a:0.0.1'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'testjava'
        dependency.artifactId.text() == 'a'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'compile'
    }

    def 'verify pom contains runtime dependencies'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:c:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                implementation 'testjava:c:0.0.1'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'testjava'
        dependency.artifactId.text() == 'c'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'runtime'
    }

    def 'verify pom contains api dependencies from java-library'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:c:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java-library'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                api 'testjava:c:0.0.1'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'testjava'
        dependency.artifactId.text() == 'c'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'compile'
    }

    def 'verify pom contains implementation dependencies from java-library'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:c:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java-library'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                implementation 'testjava:c:0.0.1'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'testjava'
        dependency.artifactId.text() == 'c'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'runtime'
    }

    def 'does not fail on no java plugin'() {
        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        noExceptionThrown()
    }
}
