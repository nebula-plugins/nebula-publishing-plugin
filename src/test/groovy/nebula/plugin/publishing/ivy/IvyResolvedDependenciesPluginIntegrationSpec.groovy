/*
 * Copyright 2015-2017 Netflix, Inc.
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

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder

class IvyResolvedDependenciesPluginIntegrationSpec extends IntegrationSpec {
    File publishDir

    def setup() {
        buildFile << """\
            ${applyPlugin(IvyResolvedDependenciesPlugin)}
            ${applyPlugin(IvyNebulaPublishPlugin)}

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
            rootProject.name = 'resolvedivytest'
            '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test.nebula/resolvedivytest/0.1.0')
    }

    def 'dynamic versions are replaced by the resolved version and have a revConstraint'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule('test.resolved:a:1.1.0').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        generator.generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ${generator.ivyRepositoryBlock}
            }

            dependencies {
                implementation 'test.resolved:a:1.+'
            }
            """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.@rev == '1.1.0'
    }

    def 'latest.* versions are replaced by the resolved version and have a revConstraint'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule('test.resolved:a:1.1.0').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        generator.generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ${generator.ivyRepositoryBlock}
            }

            dependencies {
                implementation 'test.resolved:a:latest.integration'
            }
            """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.@rev == '1.1.0'
    }

    def 'range versions are replaced by the resolved version and have a revConstraint'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:d:1.3.0')
                .addModule('test.resolved:d:1.4.1').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        generator.generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ${generator.ivyRepositoryBlock}
            }

            dependencies {
                implementation 'test.resolved:d:[1.0.0, 2.0.0['
            }
            """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def d = findDependency('d')
        d.@rev == '1.4.1'
    }

    def 'omitted versions are replaced resolved version'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule(new ModuleBuilder('test.resolved:b:1.0.0').addDependency('test.resolved:a:1.0.0').build())
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories { maven { url '${mavenrepo.absolutePath}' } }

            dependencies {
                implementation 'test.resolved:b:1.0.0'
                implementation 'test.resolved:a'
            }
            """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.@rev == '1.0.0'
    }

    def 'dependency with no changes copied through'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories { maven { url '${mavenrepo.absolutePath}' } }

            dependencies {
                implementation 'test.resolved:a:1.0.0'
            }
            """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.@rev == '1.0.0'
    }

    def 'excluded first order dependencies fail the build'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule(new ModuleBuilder('test.resolved:b:1.0.0').addDependency('test.resolved:a:1.0.0').build())
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories { maven { url '${mavenrepo.absolutePath}' } }

            configurations.all {
                exclude group: 'test.resolved', module: 'a'
            }

            dependencies {
                implementation 'test.resolved:b:1.0.0'
                implementation 'test.resolved:a'
            }
            """.stripIndent()

        when:
        def result = runTasksWithFailure('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def expectedMessage = 'Direct dependency "test.resolved:a" is excluded, delete direct dependency or stop excluding it'
        result.standardError.contains(expectedMessage) || result.standardOutput.contains(expectedMessage)
    }

    def 'project dependency is not affected by version resolving plugin'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule(new ModuleBuilder('test.resolved:b:1.0.0').addDependency('test.resolved:a:1.0.0').build())
                .build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        generator.generateTestIvyRepo()

        buildFile << """
            allprojects {
                apply plugin: 'java'
                ${applyPlugin(IvyResolvedDependenciesPlugin)}

                repositories {
                    ${generator.ivyRepositoryBlock}
                }
            }

            dependencies {
                implementation project(':sub')
            }
            """.stripIndent()

        addSubproject('sub', '''\
            group = 'nebula.hello'
            version = '1.0'

            dependencies {
                implementation 'test.resolved:b:1.+'
            }
            '''.stripIndent())

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def b = findDependency('sub')
        b.@rev == '1.0'
    }

    def findDependency(String module) {
        def root = new XmlSlurper().parseText(new File(publishDir, 'ivy-0.1.0.xml').text)
        def d = root.dependencies.dependency.find {
            it.@name == module
        }
        assert d.size() > 0: "Could not find dependency '$module'"
        return d
    }
}
