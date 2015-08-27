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
package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class MavenDependenciesPluginIntegrationSpec extends IntegrationSpec {
    File publishDir

    def setup() {
        buildFile << """\
            ${applyPlugin(MavenDependenciesPlugin)}

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

    def 'creates a jar publication'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.jar').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
    }

    def 'creates a jar publication for scala projects'() {
        buildFile << '''\
            apply plugin: 'scala'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.jar').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
    }

    def 'creates a jar publication for groovy projects'() {
        buildFile << '''\
            apply plugin: 'groovy'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.jar').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
    }

    def 'creates a war publication'() {
        buildFile << '''\
            apply plugin: 'war'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

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
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

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
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.war').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
        !new File(publishDir, 'maventest-0.1.0.jar').exists()
    }

    def 'verify pom is correct'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        String expectedPom = '''\
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <modelVersion>4.0.0</modelVersion>
              <groupId>test.nebula</groupId>
              <artifactId>maventest</artifactId>
              <version>0.1.0</version>
              <name>maventest</name>
              <description/>
            </project>
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.pom').text == expectedPom
    }

    def 'verify pom contains compile dependencies'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:a:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                compile 'testjava:a:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'testjava'
        dependency.artifactId.text() == 'a'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'runtime'
    }

    def 'verify pom contains compile dependencies for war projects'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:b:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            apply plugin: 'war'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                compile 'testjava:b:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'testjava'
        dependency.artifactId.text() == 'b'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'runtime'
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
                runtime 'testjava:c:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'testjava'
        dependency.artifactId.text() == 'c'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'runtime'
    }

    def 'verify pom contains provided dependencies for wars'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:d:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'war'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                providedCompile 'testjava:d:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'testjava'
        dependency.artifactId.text() == 'd'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'provided'
    }
}
