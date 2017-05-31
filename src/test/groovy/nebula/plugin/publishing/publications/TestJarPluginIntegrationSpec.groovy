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

import nebula.plugin.publishing.ivy.IvyPublishPlugin
import nebula.plugin.publishing.maven.MavenPublishPlugin
import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

@Deprecated
class TestJarPluginIntegrationSpec extends IntegrationSpec {
    File publishDir
    File unzipDir

    def setup() {
        def graph = new DependencyGraphBuilder()
                .addModule('test:compileDep:0.0.1')
                .addModule('test:runtimeDep:0.0.1')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            ${applyPlugin(TestJarPlugin)}
            version = '0.1.0'
            group = 'nebula'
            repositories {
                jcenter()
                maven { url '${mavenrepo.absolutePath}' }
            }
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'testjartest'
        '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/nebula/testjartest/0.1.0')
        unzipDir = new File(projectDir, 'unpacked')
    }

    def 'create test jar'() {
        setup:
        buildFile << """
            ${applyPlugin(MavenPublishPlugin)}
            ${publishingBlock('maven')}
            dependencies { testCompile 'junit:junit:4.11' }
            task unzip(type: Copy) {
                def zipFile = file('testrepo/nebula/testjartest/0.1.0/testjartest-0.1.0-tests.jar')
                def outputDir = file('unpacked')
                from zipTree(zipFile)
                into outputDir
            }
            unzip.dependsOn 'publishNebulaPublicationToTestLocalRepository'
        """
        writeHelloWorld('example')
        writeTest('src/test/java/', 'example', false)

        when:
        runTasksSuccessfully('unzip')

        then:
        def exampleTest = new File(unzipDir, 'example/HelloWorldTest.class')
        exampleTest.exists()
    }

    def 'test dependencies in pom'() {
        setup:
        buildFile << """\
            ${applyPlugin(MavenPublishPlugin)}
            ${publishingBlock('maven')}
            dependencies {
                testCompile 'test:compileDep:0.0.1'
                testRuntime 'test:runtimeDep:0.0.1'
            }
        """

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def dependencyList = new XmlSlurper().parse(new File(publishDir, 'testjartest-0.1.0.pom')).dependencies.dependency

        then:
        dependencyList.size() == 2

        when:
        def compileDep = dependencyList.find { it.artifactId == 'compileDep' }

        then:
        compileDep.groupId.text() == 'test'
        compileDep.version.text() == '0.0.1'
        compileDep.scope.text() == 'test'

        when:
        def runtimeDep = dependencyList.find { it.artifactId == 'runtimeDep' }

        then:
        runtimeDep.groupId.text() == 'test'
        runtimeDep.version.text() == '0.0.1'
        runtimeDep.scope.text() == 'test'
    }

    def 'test dependencies in ivy.xml'() {
        buildFile << """\
            ${applyPlugin(IvyPublishPlugin)}
            ${publishingBlock('ivy')}
            dependencies {
                testCompile 'test:compileDep:0.0.1'
                testRuntime 'test:runtimeDep:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaIvyPublicationToTestLocalRepository')

        def root = new XmlSlurper().parse(new File(publishDir, 'ivy-0.1.0.xml'))
        def configurationList = root.configurations.conf

        then:
        configurationList.find { it.@name == 'test' }

        when:
        def dependencyList = root.dependencies.dependency

        then:
        dependencyList.size() == 2

        when:
        def publishedArtifacts = root.publications.artifact

        then:
        publishedArtifacts.find { it.@conf == 'test' }

        when:
        def compileDep = dependencyList.find { it.@name == 'compileDep' }

        then:
        compileDep.@org == 'test'
        compileDep.@rev == '0.0.1'
        compileDep.@conf == 'test->default'

        when:
        def runtimeDep = dependencyList.find { it.@name == 'runtimeDep' }

        then:
        runtimeDep.@org == 'test'
        runtimeDep.@rev == '0.0.1'
        runtimeDep.@conf == 'test->default'
    }

    private def publishingBlock(String type) {
        """
        publishing {
            repositories {
                $type {
                    name = 'testLocal'
                    url = 'testrepo'
                }
            }
        }
        """
    }
}
