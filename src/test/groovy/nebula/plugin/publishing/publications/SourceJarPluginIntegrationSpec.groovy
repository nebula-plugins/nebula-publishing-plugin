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
package nebula.plugin.publishing.publications

import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import spock.lang.Ignore

class SourceJarPluginIntegrationSpec extends BaseIntegrationTestKitSpec {
    File mavenPublishDir
    File ivyPublishDir
    File mavenUnzipDir
    File ivyUnzipDir

    def setup() {
        buildFile << """\
            plugins {
                id 'java'
                id 'com.netflix.nebula.maven-publish'
                id 'com.netflix.nebula.ivy-publish'
                id 'com.netflix.nebula.source-jar'
            }

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

    def 'maintains backwards compatibility with sourceJar task'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        writeHelloWorld('example')

        when:
        def result = runTasks('sourceJar', '--info')

        then:
        new File(buildFile.parentFile, 'build/libs/sourcetest-0.1.0-sources.jar').exists()
    }

    def 'creates a source jar with maven publishing'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestMavenRepository')

        then:
        new File(mavenPublishDir, 'sourcetest-0.1.0-sources.jar').exists()
    }

    def 'creates a source jar with maven publishing with Gradle 8'() {
        gradleVersion = '8.4-rc-2'
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestMavenRepository')

        then:
        new File(mavenPublishDir, 'sourcetest-0.1.0-sources.jar').exists()
    }

    def 'creates a source jar with ivy publishing'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestIvyRepository')

        def ivyXmlFile = new File(ivyPublishDir, 'ivy-0.1.0.xml')

        then:
        new File(ivyPublishDir, 'sourcetest-0.1.0-sources.jar').exists()
        ivyXmlFile.exists()

        when:
        def ivyXml = new XmlSlurper().parse(ivyXmlFile)

        then:
        ivyXml.publications[0].artifact.find { it.@type == 'jar' && it.@conf == 'sourcesElements' }
    }

    def 'source jar contains java sources for maven publication'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        writeHelloWorld('example')

        when:
        runTasks('unzipMaven')

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
        runTasks('unzipIvy')

        then:
        def helloWorld = new File(ivyUnzipDir, 'example/HelloWorld.java')
        helloWorld.exists()
        helloWorld.text.contains 'public class HelloWorld'
    }

    def 'source jar contains groovy sources for maven publication'() {
        buildFile << '''\
            apply plugin: 'groovy'

            dependencies {
                implementation localGroovy()
            }
        '''.stripIndent()

        writeHelloGroovy()

        when:
        runTasks('unzipMaven')

        then:
        def helloWorld = new File(mavenUnzipDir, 'example/HelloWorld.groovy')
        helloWorld.exists()
        helloWorld.text.contains 'class HelloWorld'
    }

    def 'source jar contains groovy sources for ivy publication'() {
        buildFile << '''\
            apply plugin: 'groovy'

            dependencies {
                implementation localGroovy()
            }
        '''.stripIndent()

        writeHelloGroovy()

        when:
        runTasks('unzipIvy')

        then:
        def helloWorld = new File(ivyUnzipDir, 'example/HelloWorld.groovy')
        helloWorld.exists()
        helloWorld.text.contains 'class HelloWorld'
    }

    @Ignore
    def 'creates a source jar with maven/ivy publishing and jpi plugin'() {
        buildFile << '''\
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "org.jenkins-ci.tools:gradle-jpi-plugin:0.40.0"
  }
}

apply plugin: "org.jenkins-ci.jpi"
            
            jenkinsPlugin {
                jenkinsVersion.set('2.249.3')
            }

            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestMavenRepository', 'publishNebulaIvyPublicationToTestIvyRepository', '--warning-mode', 'none')

        then:
        new File(mavenPublishDir, 'sourcetest-0.1.0-sources.jar').exists()
        new File(ivyPublishDir, 'sourcetest-0.1.0-sources.jar').exists()
    }

    def 'maintains backwards compatibility with sourceJar task - configure baseName'() {
        buildFile << '''\
            apply plugin: 'java'
            sourcesJar.archiveBaseName = 'some-jar-name'      
        '''.stripIndent()

        writeHelloWorld('example')

        when:
        runTasks('sourceJar', '--warning-mode', 'all')

        then:
        new File(buildFile.parentFile, 'build/libs/some-jar-name-0.1.0-sources.jar').exists()
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
