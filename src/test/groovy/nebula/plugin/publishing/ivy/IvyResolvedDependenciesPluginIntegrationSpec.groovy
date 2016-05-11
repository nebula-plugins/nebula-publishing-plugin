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
package nebula.plugin.publishing.ivy

import nebula.plugin.testkit.IntegrationHelperSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.testkit.runner.GradleRunner

class IvyResolvedDependenciesPluginIntegrationSpec extends IntegrationHelperSpec {
    File publishDir

    def setup() {
        keepFiles = true

        buildFile << """\
            apply plugin: 'nebula.ivy-resolved-dependencies'

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
                compile 'test.resolved:a:1.+'
            }
        """.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        def a = findDependency('a')
        a.@rev == '1.1.0'
        a.@revConstraint == '1.+'
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
                compile 'test.resolved:a:latest.integration'
            }
        """.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        def a = findDependency('a')
        a.@rev == '1.1.0'
        a.@revConstraint == 'latest.integration'
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
                compile 'test.resolved:d:[1.0.0, 2.0.0['
            }
        """.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        def d = findDependency('d')
        d.@rev == '1.4.1'
        d.@revConstraint == '[1.0.0, 2.0.0['
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
                compile 'test.resolved:b:1.0.0'
                compile 'test.resolved:a'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.@rev == '1.0.0'
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
                apply plugin: 'nebula.ivy-resolved-dependencies'

                repositories {
                    ${generator.ivyRepositoryBlock}
                }
            }

            dependencies {
                compile project(':sub')
            }
        """.stripIndent()

        addSubproject('sub', '''\
            group = 'nebula.hello'
            version = '1.0'

            dependencies {
                compile 'test.resolved:b:1.+'
            }
        '''.stripIndent())

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def b = findDependency('sub')
        b.@rev == '1.0'
    }

    def 'conflict resolution reflected in published metadata'() {
        buildFile << """\
            apply plugin: 'java'

            repositories {
                jcenter()
            }

            dependencies {
                 compile 'com.google.guava:guava:16.0'
                 compile 'com.google.truth:truth:0.28'
            }
"""
        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def d = findDependency('guava')
        d.@rev == '18.0'
    }

    def 'module replacements reflected in published metadata'() {
        buildFile << """\
            apply plugin: 'java'

            repositories {
                jcenter()
            }

            dependencies {
                 compile 'com.google.collections:google-collections:1.0'
                 compile 'com.google.truth:truth:0.28'
                 modules {
                     module('com.google.collections:google-collections') {
                         replacedBy('com.google.guava:guava')
                     }
                 }
            }
"""
        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def d = findDependency('guava')
        d.@rev == '18.0'
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
