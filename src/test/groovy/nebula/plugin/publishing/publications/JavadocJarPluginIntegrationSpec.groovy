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

class JavadocJarPluginIntegrationSpec extends BaseIntegrationTestKitSpec {
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
                id 'com.netflix.nebula.javadoc-jar'
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
                def zipFile = file('testmaven/test/nebula/javadoctest/0.1.0/javadoctest-0.1.0-javadoc.jar')
                def outputDir = file('unpackedMaven')

                from zipTree(zipFile)
                into outputDir
            }

            unzipMaven.dependsOn 'publishNebulaPublicationToTestMavenRepository'

            task unzipIvy(type: Copy) {
                def zipFile = file('testivy/test.nebula/javadoctest/0.1.0/javadoctest-0.1.0-javadoc.jar')
                def outputDir = file('unpackedIvy')

                from zipTree(zipFile)
                into outputDir
            }

            unzipIvy.dependsOn 'publishNebulaIvyPublicationToTestIvyRepository'
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'javadoctest'
        '''.stripIndent()

        mavenPublishDir = new File(projectDir, 'testmaven/test/nebula/javadoctest/0.1.0')
        ivyPublishDir = new File(projectDir, 'testivy/test.nebula/javadoctest/0.1.0')
        mavenUnzipDir = new File(projectDir, 'unpackedMaven')
        ivyUnzipDir = new File(projectDir, 'unpackedIvy')
    }

    def 'javadoc jar is created for maven'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestMavenRepository')

        then:
        new File(mavenPublishDir, 'javadoctest-0.1.0-javadoc.jar').exists()
    }

    def 'javadoc jar has content for maven'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        createHelloWorld()

        when:
        runTasks('unzipMaven')

        then:
        new File(mavenUnzipDir, 'example/HelloWorld.html').exists()
    }

    def 'javadoc jar is created for ivy'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestIvyRepository')

        def ivyXmlFile = new File(ivyPublishDir, 'ivy-0.1.0.xml')

        then:
        new File(ivyPublishDir, 'javadoctest-0.1.0-javadoc.jar').exists()
        ivyXmlFile.exists()

        when:
        def ivyXml = new XmlSlurper().parse(ivyXmlFile)

        then:
        ivyXml.publications[0].artifact.find { it.@type == 'jar' && it.@conf == 'javadocElements' }
    }

    def 'javadoc jar has content for ivy'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        createHelloWorld()

        when:
        runTasks('unzipIvy')

        then:
        new File(ivyUnzipDir, 'example/HelloWorld.html').exists()
    }

    @Ignore //TODO: fix me
    def 'creates a javadoc jar with maven/ivy publishing and jpi plugin'() {
        buildFile << '''\
buildscript {
  repositories {
    maven {
      url = "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "org.jenkins-ci.tools:gradle-jpi-plugin:0.40.0"
  }
}

apply plugin: "org.jenkins-ci.jpi"

            apply plugin: 'java'
            
            jenkinsPlugin {
                jenkinsVersion.set('2.249.3')
            }
        '''.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestMavenRepository', 'publishNebulaIvyPublicationToTestIvyRepository', '--warning-mode', 'none')

        then:
        new File(mavenPublishDir, 'javadoctest-0.1.0-javadoc.jar').exists()
        new File(ivyPublishDir, 'javadoctest-0.1.0-javadoc.jar').exists()
    }

    private void createHelloWorld() {
        def src = new File(projectDir, 'src/main/java/example')
        src.mkdirs()

        new File(src, 'HelloWorld.java').text = '''\
            package example;

            /**
             * HelloWorld class for test
             */
            public class HelloWorld {
            }
        '''.stripIndent()
    }
}
