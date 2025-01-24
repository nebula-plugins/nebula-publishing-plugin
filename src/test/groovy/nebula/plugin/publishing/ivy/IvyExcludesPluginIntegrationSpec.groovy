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
package nebula.plugin.publishing.ivy

import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.testkit.runner.BuildResult

class IvyExcludesPluginIntegrationSpec extends BaseIntegrationTestKitSpec {
    File publishDir

    def setup() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.ivy-nebula-publish'
            }

            version = '0.1.0'
            group = 'test.nebula'

            publishing {
                repositories {
                    ivy {
                        name = 'testLocal'
                        url = 'testrepo'
                    }
                }
            }
            """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'ivytest'
            '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test.nebula/ivytest/0.1.0')
    }

    def 'excludes are added to dependencies as needed'() {
        setup:
        def graph = new DependencyGraphBuilder()
                .addModule('testjava:a:0.0.1')
                .addModule('testjava:b:0.0.1')
                .addModule('testjava:ex1:0.0.1')
                .addModule('testjava:ex2:0.0.1')
                .build()
        File ivyrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ivy { url = '${ivyrepo.absolutePath}' }
            }

            dependencies {
                runtimeOnly('testjava:a:0.0.1') {
                    exclude group: 'testjava', module: 'ex1'
                    exclude module: 'ex2'
                }
                runtimeOnly('testjava:b:0.0.1') {
                    exclude group: 'testjava'
                }
            }
            """.stripIndent()

        when:
        BuildResult result = runTasks('publishNebulaIvyPublicationToTestLocalRepository')
        println result.output

        def dependencies = new XmlSlurper().parse(new File(publishDir, 'ivy-0.1.0.xml')).dependencies.dependency
        def a = dependencies.find { it.@name == 'a' }
        def b = dependencies.find { it.@name == 'b' }

        then:
        def aChildren = a.children()
        aChildren.size() == 2
        aChildren.find { it.@org == 'testjava' && it.@module == 'ex1' }
        aChildren.find { !it.attributes().org && it.@module == 'ex2' }

        then:
        def bChildren = b.children()
        bChildren.size() == 1
        bChildren.find { it.@org == 'testjava' && !it.attributes().module }
    }
}
