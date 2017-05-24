/*
 * Copyright 2017 Netflix, Inc.
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

class MavenCompileOnlyPluginIntegrationSpec extends IntegrationTestKitSpec {
    File publishDir

    def setup() {
        buildFile << """\
            plugins {
                id 'nebula.maven-compile-only'
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

    def 'verify pom contains compileOnly dependencies'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:c:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                compileOnly 'testjava:c:0.0.1'
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
        dependency.scope.text() == 'provided'
    }
}
