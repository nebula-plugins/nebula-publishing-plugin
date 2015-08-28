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
package nebula.plugin.publishing.publications

import nebula.plugin.publishing.ivy.IvyPublishPlugin
import nebula.plugin.publishing.maven.MavenPublishPlugin
import nebula.test.IntegrationSpec

class SourceJarPluginIntegrationSpec extends IntegrationSpec {
    File mavenPublishDir
    File ivyPublishDir
    File mavenUnzipDir
    File ivyUnzipDir

    def setup() {
        buildFile << """\
            ${applyPlugin(MavenPublishPlugin)}
            ${applyPlugin(IvyPublishPlugin)}
            ${applyPlugin(SourceJarPlugin)}

            version = '0.1.0'
            group = 'test.nebula'

            publishing {
                repositories {
                    maven {
                        name = 'testMaven'
                        url = 'testmaven'
                    }
                    ivy {
                        name = 'testIvy'
                        url = 'testivy'
                    }
                }
            }

            task unzipMaven(type: Copy) {
                def zipFile = file('testmaven/test/nebula/sourcetest/0.1.0/sourcetest-0.1.0-sources.jar')
                def outputDir = file('unpackedMaven')

                from zipTree(zipFile)
                into outputDir
            }

            unzipMaven.dependsOn 'publishNebulaPublicationToTestMavenRepository'

            task unzipIvy(type: Copy) {
                def zipFile = file('testivy/test.nebula/sourcetest/0.1.0/sourcetest-0.1.0-sources.jar')
                def outputDir = file('unpackedIvy')

                from zipTree(zipFile)
                into outputDir
            }

            unzipIvy.dependsOn 'publishNebulaIvyPublicationToTestIvyRepository'
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'sourcetest'
        '''.stripIndent()

        mavenPublishDir = new File(projectDir, 'testmaven/test/nebula/sourcetest/0.1.0')
        ivyPublishDir = new File(projectDir, 'testivy/test.nebula/sourcetest/0.1.0')
        mavenUnzipDir = new File(projectDir, 'unpackedMaven')
        ivyUnzipDir = new File(projectDir, 'unpackedIvy')
    }

    def 'creates a source jar with maven publishing'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestMavenRepository')

        then:
        new File(mavenPublishDir, 'sourcetest-0.1.0-sources.jar').exists()
    }

    def 'creates a source jar with ivy publishing'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaIvyPublicationToTestIvyRepository')

        then:
        new File(ivyPublishDir, 'sourcetest-0.1.0-sources.jar').exists()
    }

    def 'source jar contains java sources for maven publication'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        writeHelloWorld('example')

        when:
        runTasksSuccessfully('unzipMaven')

        then:
        def helloWorld = new File(mavenUnzipDir, 'example/HelloWorld.java')
        helloWorld.exists()
        helloWorld.text.contains 'public class HelloWorld'
    }

    def 'source jar contains java sources for ivy publication'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        writeHelloWorld('example')

        when:
        runTasksSuccessfully('unzipIvy')

        then:
        def helloWorld = new File(ivyUnzipDir, 'example/HelloWorld.java')
        helloWorld.exists()
        helloWorld.text.contains 'public class HelloWorld'
    }

    def 'source jar contains groovy sources for maven publication'() {
        buildFile << '''\
            apply plugin: 'groovy'

            dependencies {
                compile localGroovy()
            }
        '''.stripIndent()

        writeHelloGroovy()

        when:
        runTasksSuccessfully('unzipMaven')

        then:
        def helloWorld = new File(mavenUnzipDir, 'example/HelloWorld.groovy')
        helloWorld.exists()
        helloWorld.text.contains 'class HelloWorld'
    }

    def 'source jar contains groovy sources for ivy publication'() {
        buildFile << '''\
            apply plugin: 'groovy'

            dependencies {
                compile localGroovy()
            }
        '''.stripIndent()

        writeHelloGroovy()

        when:
        runTasksSuccessfully('unzipIvy')

        then:
        def helloWorld = new File(ivyUnzipDir, 'example/HelloWorld.groovy')
        helloWorld.exists()
        helloWorld.text.contains 'class HelloWorld'
    }

    private void writeHelloGroovy() {
        def dir = new File(projectDir, 'src/main/groovy/example')
        dir.mkdirs()
        def example = new File(dir, 'HelloWorld.groovy')
        example.text = '''\
            package example

            class HelloWorld {
                static void main(String[] args) {
                    println 'Hello world'
                }
            }
        '''.stripIndent()
    }
}
