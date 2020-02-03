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

import nebula.test.IntegrationTestKitSpec

class IvyNebulaShadowJarPublishPluginIntegrationSpec extends IntegrationTestKitSpec {
    def setup() {
        debug = true
        keepFiles = true
        buildFile << """\
            plugins {
                id 'nebula.ivy-publish'
                id "com.github.johnrengelman.shadow" version "5.2.0"
                id 'java'
                id "nebula.info" version "5.2.0"
                id "nebula.contacts" version "5.1.0"
            }

            contacts {
                'nebula@example.test' {
                    moniker 'Nebula'
                }
            }
            version = '0.1.0'
            group = 'test.nebula'

            repositories {
                mavenCentral()
            }
            
            dependencies {
               implementation 'com.google.guava:guava:19.0'
            }
            
             publishing {
                repositories {
                    ivy {
                        name 'distIvy'
                        url project.file("\${project.buildDir}/distIvy").toURI().toURL()
                    }
                }
            }
            
             jar {
              enabled = false // this configuration is used to produce only the shadowed jar
            }

            jar.dependsOn shadowJar // this configuration is used to produce only the shadowed jar            
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'ivypublishingtest'
        '''.stripIndent()
    }

    def 'publish shadow jar with proper Ivy descriptor - no classifier'() {
        setup:
        buildFile << """
            shadowJar {
                classifier null // this configuration is used to produce only the shadowed jar
               relocate 'com.google', 'com.netflix.shading.google'
            }
"""

        writeJavaSourceFile("""
package demo;

public class DemoApplication {
    public static void main(String[] args) {
        System.out.println(args);
    }
}

""")
        when:
        def result = runTasks('shadowJar', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        def root = new XmlSlurper().parse(new File(projectDir, 'build/distIvy/test.nebula/ivypublishingtest/0.1.0/ivy-0.1.0.xml'))
        root.info.@organisation == 'test.nebula'
        root.info.@module == 'ivypublishingtest'
        root.info.@revision == '0.1.0'
        root.info.@status == 'integration'
        def artifact = root.publications.artifact[0]
        artifact.@name == 'ivypublishingtest'
        artifact.@type == 'jar'

        def desc = root
                .declareNamespace([nebula: 'http://netflix.com/build'])
                .info[0].description[0]
        desc.children().size() > 1
        desc.'nebula:Implementation_Version' == '0.1.0'
        desc.'nebula:Implementation_Title' == 'test.nebula#ivypublishingtest;0.1.0'
        desc.'nebula:Module_Email' == 'nebula@example.test'

        and:
        assertDependency('com.google.guava', 'guava', '19.0', 'runtime->default')

        when:
        def jar = new File(projectDir, "build/libs/ivypublishingtest-0.1.0.jar")

        then:
        jar.exists()
    }

    def 'publish shadow jar with proper Ivy descriptor - with classifier'() {
        setup:
        buildFile << """
            shadowJar {
               classifier 'all' // this configuration is used to produce only the shadowed jar
               relocate 'com.google', 'com.netflix.shading.google'
            }
"""

        writeJavaSourceFile("""
package demo;

public class DemoApplication {
    public static void main(String[] args) {
        System.out.println(args);
    }
}

""")
        when:
        def result = runTasks('shadowJar', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        def root = new XmlSlurper().parse(new File(projectDir, 'build/distIvy/test.nebula/ivypublishingtest/0.1.0/ivy-0.1.0.xml'))
        root.info.@organisation == 'test.nebula'
        root.info.@module == 'ivypublishingtest'
        root.info.@revision == '0.1.0'
        root.info.@status == 'integration'
        def artifact = root.publications.artifact[0]
        artifact.@name == 'ivypublishingtest'
        artifact.@type == 'jar'

        def desc = root
                .declareNamespace([nebula: 'http://netflix.com/build'])
                .info[0].description[0]
        desc.children().size() > 1
        desc.'nebula:Implementation_Version' == '0.1.0'
        desc.'nebula:Implementation_Title' == 'test.nebula#ivypublishingtest;0.1.0'
        desc.'nebula:Module_Email' == 'nebula@example.test'

        and:
        assertDependency('com.google.guava', 'guava', '19.0', 'runtime->default')

        when:
        def jar = new File(projectDir, "build/libs/ivypublishingtest-0.1.0-all.jar")

        then:
        jar.exists()
    }

    def 'publish shadow jar with proper Ivy descriptor - no classifier - manipulate xml'() {
        setup:
        buildFile << """
            shadowJar {
                classifier null // this configuration is used to produce only the shadowed jar
               relocate 'com.google', 'com.netflix.shading.google'
            }

            
            afterEvaluate {
             publishing {
              publications {
               // to remove shaded dependency from ivy.xml
               withType(IvyPublication) {
                descriptor.withXml {
                 asNode()
                   .dependencies
                   .dependency
                   .findAll {
                    it.@name == "guava"
                   }
                   .each { it.parent().remove(it) }
                }
               }
               // to remove shaded dependency from pom.xml
               withType(MavenPublication) {
                pom.withXml {
                 asNode()
                   .dependencies
                   .dependency
                   .findAll {
                    it.artifactId.text() == "guava"
                   }
                   .each { it.parent().remove(it) }
                }
               }
              }
             }
            } 
"""

        writeJavaSourceFile("""
package demo;

public class DemoApplication {
    public static void main(String[] args) {
        System.out.println(args);
    }
}

""")
        when:
        def result = runTasks('shadowJar', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        def root = new XmlSlurper().parse(new File(projectDir, 'build/distIvy/test.nebula/ivypublishingtest/0.1.0/ivy-0.1.0.xml'))
        root.info.@organisation == 'test.nebula'
        root.info.@module == 'ivypublishingtest'
        root.info.@revision == '0.1.0'
        root.info.@status == 'integration'
        def artifact = root.publications.artifact[0]
        artifact.@name == 'ivypublishingtest'
        artifact.@type == 'jar'

        def desc = root
                .declareNamespace([nebula: 'http://netflix.com/build'])
                .info[0].description[0]
        desc.children().size() > 1
        desc.'nebula:Implementation_Version' == '0.1.0'
        desc.'nebula:Implementation_Title' == 'test.nebula#ivypublishingtest;0.1.0'
        desc.'nebula:Module_Email' == 'nebula@example.test'

        and:
        !findDependency('com.google.guava', 'guava')

        when:
        def jar = new File(projectDir, "build/libs/ivypublishingtest-0.1.0.jar")

        then:
        jar.exists()
    }

    def findDependency(String org, String name) {
        def dependencies = new XmlSlurper().parse(new File(projectDir, 'build/distIvy/test.nebula/ivypublishingtest/0.1.0/ivy-0.1.0.xml')).dependencies.dependency
        return dependencies.find { it.@name == name && it.@org == org }
    }

    boolean assertDependency(String org, String name, String rev, String conf = null) {
        def found = findDependency(org, name)
        assert found
        assert found.@rev == rev
        assert !conf || found.@conf == conf
        found
    }
}
