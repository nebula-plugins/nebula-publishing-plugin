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
package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class MavenPublishingIntegrationSpec extends IntegrationSpec {
    File publishDir

    def setup() {
        buildFile << """\
            ${applyPlugin(MavenPublishingPlugin)}

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
    }

    def 'all of the features work together'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test:a:0.0.1')
                .addModule('test:b:1.9.2')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            apply plugin: 'war'
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
                providedCompile 'test:b:[1.0.0, 2.0.0)'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.version == '0.1.0'
        def dependencies = pom.dependencies.dependency
        def a = dependencies.find { it.artifactId == 'a' }
        a.version == '0.0.1'
        def b = dependencies.find { it.artifactId == 'b' }
        b.version == '1.9.2'
        b.scope == 'provided'
        pom.developers.developer[0].name == 'Nebula'
        pom.properties.nebula_Module_Owner == 'nebula@example.test'
        pom.url != null
    }
}
