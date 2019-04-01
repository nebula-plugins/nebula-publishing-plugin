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
package nebula.plugin.publishing.maven

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.testkit.runner.UnexpectedBuildFailure

class MavenResolvedDependenciesPluginIntegrationSpec extends IntegrationTestKitSpec {
    File publishDir

    def setup() {
        buildFile << """\
            plugins {
                id 'nebula.maven-resolved-dependencies'
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
            rootProject.name = 'resolvedmaventest'
        '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test/nebula/resolvedmaventest/0.1.0')
    }

    def 'dynamic versions are replaced by the resolved version'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule('test.resolved:a:1.1.0').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories { maven { url '${mavenrepo.absolutePath}' } }

            dependencies {
                compile 'test.resolved:a:1.+'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.version.text() == '1.1.0'
    }

    def 'dynamic latest versions are replaced by the resolved version'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule('test.resolved:a:1.1.0').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories { maven { url '${mavenrepo.absolutePath}' } }

            dependencies {
                compile 'test.resolved:a:latest.release'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.version.text() == '1.1.0'
    }

    def 'handle maven style dynamic versions'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:d:1.3.0')
                .addModule('test.resolved:d:1.4.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories { maven { url '${mavenrepo.absolutePath}' } }

            dependencies {
                compile 'test.resolved:d:[1.0.0, 2.0.0)'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def d = findDependency('d')
        d.version.text() == '1.4.1'
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
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.version.text() == '1.0.0'
    }

    def 'project dependency is not affected by version resolving plugin'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule(new ModuleBuilder('test.resolved:b:1.0.0').addDependency('test.resolved:a:1.0.0').build())
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile.text = """\
            plugins {
                id 'nebula.maven-resolved-dependencies'
                id 'nebula.maven-nebula-publish'
            }
            allprojects {
                apply plugin: 'java'
                apply plugin: 'nebula.maven-resolved-dependencies'
                
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

                repositories { maven { url '${mavenrepo.absolutePath}' } }
            }

            dependencies {
                compile project(':sub')
            }
            """.stripIndent()

        addSubproject('sub', '''\
            group = 'nebula.hello'
            version = '1.0'
        '''.stripIndent())

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def b = findDependency('sub')
        b.version.text() == '1.0'
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
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def d = findDependency('guava')
        d.version == '18.0'
    }

    def 'works with java-library plugin and dependency-recommender'() {
        buildFile.text = '''\
            plugins {
                id 'nebula.maven-resolved-dependencies'
                id 'nebula.maven-nebula-publish'
                id 'java-library'
                id 'nebula.dependency-recommender' version '4.1.0'
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
            
            repositories { jcenter() }
            dependencies {
                api 'com.google.truth:truth'
                implementation 'com.google.collections:google-collections'
            }
            
            dependencyRecommendations {
                map recommendations: [
                    'com.google.truth:truth': '0.28',
                    'com.google.collections:google-collections': '1.0'
                ]
            }
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        findDependencyInScope('truth', 'compile').version == '0.28'
        findDependencyInScope('google-collections', 'runtime').version == '1.0'
    }

    def 'dependency with no changes copied through'() {
        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories { maven { url '${mavenrepo.absolutePath}' } }

            dependencies {
                compile 'test.resolved:a:1.0.0'
            }
            """.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.version == '1.0.0'
    }

    def findDependency(String artifactId) {
        def root = new XmlSlurper().parseText(new File(publishDir, 'resolvedmaventest-0.1.0.pom').text)
        def d = root.dependencies.dependency.find {
            it.artifactId == artifactId
        }
        assert d.size() > 0: "Could not find dependency '$artifactId'"
        return d
    }

    def findDependencyInScope(String artifactId, String scope) {
        def root = new XmlSlurper().parseText(new File(publishDir, 'resolvedmaventest-0.1.0.pom').text)
        def d = root.dependencies.dependency.find {
            it.artifactId == artifactId && it.scope == scope
        }
        assert d.size() > 0: "Could not find dependency '$artifactId' in scope '$scope'"
        return d
    }
}
