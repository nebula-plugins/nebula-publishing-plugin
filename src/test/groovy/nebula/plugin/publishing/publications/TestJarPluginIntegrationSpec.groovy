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
package nebula.plugin.publishing.publications

import nebula.plugin.publishing.maven.MavenPublishingPlugin
import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class TestJarPluginIntegrationSpec extends IntegrationSpec {
    File publishDir
    File unzipDir

    def setup() {
        buildFile << """\
            ${applyPlugin(MavenPublishingPlugin)}
            ${applyPlugin(TestJarPlugin)}

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

            task unzip(type: Copy) {
                def zipFile = file('testrepo/test/nebula/maventest/0.1.0/maventest-0.1.0-tests.jar')
                def outputDir = file('unpacked')

                from zipTree(zipFile)
                into outputDir
            }

            unzip.dependsOn 'publishNebulaPublicationToTestLocalRepository'
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'maventest'
        '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test/nebula/maventest/0.1.0')
        unzipDir = new File(projectDir, 'unpacked')
    }

    def 'create test jar'() {
        buildFile << '''\
            apply plugin: 'java'

            repositories { jcenter() }
            dependencies {
                testCompile 'junit:junit:4.11'
            }
        '''.stripIndent()

        writeHelloWorld('example')
        writeTest('src/test/java/', 'example', false)

        when:
        runTasksSuccessfully('unzip')

        then:
        def exampleTest = new File(unzipDir, 'example/HelloWorldTest.class')
        exampleTest.exists()
    }

    def 'test dependencies in pom'() {
        buildFile << '''\
            apply plugin: 'java'

            repositories { jcenter() }
            dependencies {
                testCompile 'junit:junit:4.11'
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'junit'
        dependency.artifactId.text() == 'junit'
        dependency.version.text() == '4.11'
        dependency.scope.text() == 'test'
    }

    def 'test and compile dependencies in pom'() {
        def graph = new DependencyGraphBuilder().addModule('test.compile:a:0.0.1').addModule('test.testcompile:b:0.1.0').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                compile 'test.compile:a:0.0.1'
                testCompile 'test.testcompile:b:0.1.0'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependencyList = root.dependencies.dependency

        dependencyList.size() == 2
        def a = dependencyList.find { node -> node.artifactId == 'a' }
        a.groupId.text() == 'test.compile'
        a.version.text() == '0.0.1'
        a.scope.text() == 'runtime'
        def b = dependencyList.find { node -> node.artifactId == 'b' }
        b.groupId.text() == 'test.testcompile'
        b.version.text() == '0.1.0'
        b.scope.text() == 'test'
    }

    def 'testRuntime dependencies in pom'() {
        def graph = new DependencyGraphBuilder().addModule('test.testruntime:a:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                testRuntime 'test.testruntime:a:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependencyList = root.dependencies.dependency

        dependencyList.size() == 1
        def a = dependencyList.find { node -> node.artifactId == 'a' }
        a.groupId.text() == 'test.testruntime'
        a.version.text() == '0.0.1'
        a.scope.text() == 'test'
    }
}
