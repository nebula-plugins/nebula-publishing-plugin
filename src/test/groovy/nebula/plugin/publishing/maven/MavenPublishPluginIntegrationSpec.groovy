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

import groovy.json.JsonSlurper
import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.util.GradleVersion

class MavenPublishPluginIntegrationSpec extends IntegrationTestKitSpec {
    def setup() {
        debug = true
        keepFiles = true
        buildFile << """\
            plugins {
                id 'nebula.maven-publish'
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
            rootProject.name = 'mavenpublishingtest'
        '''.stripIndent()

        if (GradleVersion.current().baseVersion < GradleVersion.version("6.0")) {
            settingsFile << '''\
                enableFeaturePreview("GRADLE_METADATA")
            '''.stripIndent()
        }
    }

    def 'publish POM with resolved dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test:a:0.0.1')
                .addModule('test:b:1.9.2')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            apply plugin: 'nebula.contacts'
            apply plugin: 'nebula.info'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            contacts {
                'nebula@example.test' {
                    moniker 'Nebula'
                }
            }

            dependencies {
                compile 'test:a:0.+'
                compile 'test:b:[1.0.0, 2.0.0)'
            }
        """.stripIndent()

        when:
        runTasks('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.version == '0.1.0'
        pom.developers.developer[0].name == 'Nebula'
        pom.properties.nebula_Module_Owner == 'nebula@example.test'
        pom.url != null

        when:
        def dependencies = pom.dependencies.dependency
        def a = dependencies.find { it.artifactId == 'a' }

        then:
        a.version == '0.0.1'

        when:
        def b = dependencies.find { it.artifactId == 'b' }

        then:
        b.version == '1.9.2'
    }

    def 'produces gradle metadata file'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test:a:0.0.1')
                .addModule('test:b:1.9.2')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            apply plugin: 'nebula.contacts'
            apply plugin: 'nebula.info'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            contacts {
                'nebula@example.test' {
                    moniker 'Nebula'
                }
            }

            dependencies {
                compile 'test:a:0.+'
                compile 'test:b:[1.0.0, 2.0.0)'
            }
        """.stripIndent()

        when:
        runTasks('generateMetadataFileForNebulaPublication')


        then:
        def moduleJson = new JsonSlurper().parse(new File(projectDir, 'build/publications/nebula/module.json'))
        def runtimeVariant = moduleJson.variants.find { it.name == 'runtimeElements'}
        def dependencies = runtimeVariant.dependencies
        dependencies.size() == 2

        when:
        def a = dependencies.find { it.module == 'a' }

        then:
        a.version.requires == '0.0.1'

        when:
        def b = dependencies.find { it.module == 'b' }

        then:
        b.version.requires == '1.9.2'
    }

}
