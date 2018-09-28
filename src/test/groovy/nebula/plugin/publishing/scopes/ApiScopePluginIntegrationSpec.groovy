/*
 * Copyright 2016-2017 Netflix, Inc.
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
package nebula.plugin.publishing.scopes

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.testkit.runner.BuildResult

class ApiScopePluginIntegrationSpec extends IntegrationTestKitSpec {
    def 'jar from compileApi exists in runtime classpath'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        generator.generateTestMavenRepo()

        settingsFile << "rootProject.name='jar-from-compileApi-exists-in-runtime-classpath'"
        buildFile << """\
            plugins {
                id 'java'
                id 'nebula.compile-api'
            }

            repositories {
                ${generator.mavenRepositoryBlock}
            }

            dependencies {
                compileApi 'test.nebula:a:1.0.0'
            }
            """.stripIndent()

        when:
        BuildResult result = runTasks('dependencies', '--configuration', 'runtime')

        then:
        result.output.contains('\\--- test.nebula:a:1.0.0')
    }

    def 'compileApi is put in maven compile scope'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        generator.generateTestMavenRepo()

        settingsFile << '''\
            rootProject.name = 'testmaven'
            '''.stripIndent()

        buildFile << """\
            plugins {
                id 'java'
                id 'nebula.compile-api'
                id 'maven-publish'
            }

            group = 'test.nebula'
            version = '0.1.0'

            repositories {
                ${generator.mavenRepositoryBlock}
            }

            dependencies {
                compileApi 'test.nebula:a:1.0.0'
            }

            publishing {
                repositories {
                    maven {
                        name 'testLocal'
                        url 'build/testLocal'
                    }
                }
                publications {
                    testNebula(MavenPublication) {
                        from components.java
                    }
                }
            }
            """.stripIndent()

        when:
        BuildResult result = runTasks('publishTestNebulaPublicationToTestLocalRepository')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/testLocal/test/nebula/testmaven/0.1.0/testmaven-0.1.0.pom'))
        def dependency = pom.dependencies.dependency.find { it.artifactId == 'a' }
        dependency.artifactId == 'a'
        dependency.scope == 'compile'
    }

    def 'dependencies not in compileApi are not switched to compile scope in the pom'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:b:1.0.0')
                .build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        generator.generateTestMavenRepo()

        settingsFile << '''\
            rootProject.name = 'testmaven'
            '''.stripIndent()

        buildFile << """\
            plugins {
                id 'java'
                id 'nebula.compile-api'
                id 'maven-publish'
            }

            group = 'test.nebula'
            version = '0.1.0'

            repositories {
                ${generator.mavenRepositoryBlock}
            }

            dependencies {
                compileApi 'test.nebula:a:1.0.0'
                compile 'test.nebula:b:1.0.0'
            }

            publishing {
                repositories {
                    maven {
                        name 'testLocal'
                        url 'build/testLocal'
                    }
                }
                publications {
                    testNebula(MavenPublication) {
                        from components.java
                    }
                }
            }
            """.stripIndent()

        when:
        BuildResult result = runTasks('publishTestNebulaPublicationToTestLocalRepository')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/testLocal/test/nebula/testmaven/0.1.0/testmaven-0.1.0.pom'))
        def b = pom.dependencies.dependency.find { it.artifactId == 'b' }
        b.artifactId == 'b'
        b.scope == 'compile'
    }

    def 'compileApi is put in ivy compile scope'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")
        generator.generateTestIvyRepo()

        settingsFile << '''\
            rootProject.name = 'testivy'
            '''.stripIndent()

        buildFile << """\
            plugins {
                id 'java'
                id 'nebula.compile-api'
                id 'ivy-publish'
            }

            group = 'test.nebula'
            version = '0.1.0'

            repositories {
                ${generator.ivyRepositoryBlock}
            }

            dependencies {
                compileApi 'test.nebula:a:1.0.0'
            }

            publishing {
                repositories {
                    ivy {
                        name 'testLocal'
                        url 'build/testLocal'
                    }
                }
                publications {
                    testNebula(IvyPublication) {
                        from components.java
                    }
                }
            }
            """.stripIndent()

        when:
        BuildResult result = runTasks('publishTestNebulaPublicationToTestLocalRepository')

        then:
        def ivy = new XmlSlurper().parse(new File(projectDir, 'build/testLocal/test.nebula/testivy/0.1.0/ivy-0.1.0.xml'))
        def a = ivy.dependencies.dependency.find { it.@name == 'a' }
        a.@name == 'a'
        a.@conf == 'compile->default'
    }
}
