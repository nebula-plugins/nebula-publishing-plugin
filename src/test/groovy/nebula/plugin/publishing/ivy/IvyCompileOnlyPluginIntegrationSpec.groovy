/*
 * Copyright 2017-2020 Netflix, Inc.
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

class IvyCompileOnlyPluginIntegrationSpec extends BaseIntegrationTestKitSpec {
    File publishDir

    def setup() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.ivy-nebula-publish'
                id 'com.netflix.nebula.ivy-compile-only'
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

    def 'verify ivy contains compileOnly dependencies'() {
        keepFiles = true
        def graph = new DependencyGraphBuilder().addModule('testjava:c:0.0.1').build()
        File ivyrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ivy { url '${ivyrepo.toURI().toURL()}' }
            }

            dependencies {
                compileOnly 'testjava:c:0.0.1'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'ivy-0.1.0.xml').text)
        def dependency = root.dependencies.dependency[0]
        dependency.@org == 'testjava'
        dependency.@name == 'c'
        dependency.@rev == '0.0.1'
        dependency.@conf == 'provided'
    }

    def 'verify ivy contains compileOnly dependencies together with global excludes'() {
        keepFiles = true
        def graph = new DependencyGraphBuilder().addModule('testjava:c:0.0.1').build()
        File ivyrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ivy { url '${ivyrepo.toURI().toURL()}' }
            }
            
            configurations.all {
                exclude group: 'org.slf4j', module: 'slf4j-api'
            }

            dependencies {
                compileOnly 'testjava:c:0.0.1'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'ivy-0.1.0.xml').text)
        def dependency = root.dependencies.dependency[0]
        dependency.@org == 'testjava'
        dependency.@name == 'c'
        dependency.@rev == '0.0.1'
        dependency.@conf == 'provided'
    }
}
