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

import nebula.plugin.publishing.maven.MavenPublishPlugin
import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class TestJarPluginIntegrationSpec extends IntegrationSpec {
    File publishDir
    File unzipDir

    def setup() {
        buildFile << """\
            ${applyPlugin(MavenPublishPlugin)}
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
        def graph = new DependencyGraphBuilder()
                .addModule('test:compileDep:0.0.1')
                .addModule('test:runtimeDep:0.0.1')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            dependencies {
                testCompile 'test:compileDep:0.0.1'
                testRuntime 'test:runtimeDep:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependencyList = root.dependencies.dependency

        then:
        dependencyList.size() == 2

        when:
        def compileDep = dependencyList.find { it.artifactId == 'compileDep' }

        then:
        compileDep.groupId.text() == 'test'
        compileDep.artifactId.text() == 'compileDep'
        compileDep.version.text() == '0.0.1'
        compileDep.scope.text() == 'test'

        when:
        def runtimeDep = dependencyList.find { it.artifactId == 'runtimeDep' }

        then:
        runtimeDep.groupId.text() == 'test'
        runtimeDep.artifactId.text() == 'runtimeDep'
        runtimeDep.version.text() == '0.0.1'
        runtimeDep.scope.text() == 'test'
    }
}
