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
package nebula.plugin.publishing.maven

import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder

class MavenExcludesPluginIntegrationSpec extends BaseIntegrationTestKitSpec {
    def setup() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.maven-nebula-publish'
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
            rootProject.name = 'excludesmaven'
        '''.stripIndent()
    }

    def 'add excludes to dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test:a:1.0.0')
                .addDependency('test:b:2.0.0')
                .addDependency('test:c:0.9.0')
                .build())
                .addModule('test:b:1.9.2')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                implementation('test:a:1.0.0') {
                    exclude group: 'test', module: 'b'
                }
            }
        """.stripIndent()

        when:
        runTasks('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.dependencies.dependency[0].exclusions.exclusion.size() == 1
        def aExclusion = pom.dependencies.dependency[0].exclusions.exclusion[0]
        aExclusion.groupId == 'test'
        aExclusion.artifactId == 'b'
    }
}
