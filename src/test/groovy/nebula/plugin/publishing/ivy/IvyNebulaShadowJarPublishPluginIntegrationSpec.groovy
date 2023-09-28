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
package nebula.plugin.publishing.ivy

import nebula.test.IntegrationTestKitSpec
import spock.lang.Unroll

class IvyNebulaShadowJarPublishPluginIntegrationSpec extends IntegrationTestKitSpec {
    def setup() {
        debug = true
        keepFiles = true
        buildFile << """\
            buildscript {
               configurations.named('classpath').configure {
                  resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                       if (details.requested.group == 'org.ow2.asm') {
                            details.useVersion '9.5'
                            details.because "Asm 9.5 is required for JDK 21 support"
                      }
                  }
                }
            }
            plugins {
                id 'com.netflix.nebula.ivy-publish'
                id "com.github.johnrengelman.shadow" version "8.1.1"
                id 'java'
                id "com.netflix.nebula.info" version "12.1.3"
                id "com.netflix.nebula.contacts" version "7.0.0"
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
                        url project.file("\${project.layout.buildDirectory.getAsFile().get()}/distIvy").toURI().toURL()
                    }
                }
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
            jar {
              enabled = false // this configuration is used to produce only the shadowed jar
            }
            
            shadowJar {
                archiveClassifier.set(null) // this configuration is used to produce only the shadowed jar
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
        and:
        fileWasPublished('ivypublishingtest-0.1.0.jar')
        fileWasPublished('ivypublishingtest-0.1.0.jar.sha1')
        fileWasPublished('ivypublishingtest-0.1.0.jar.sha256')
        fileWasPublished('ivypublishingtest-0.1.0.jar.sha512')

        !fileWasPublished('ivypublishingtest-0.1.0-all.jar')
        !fileWasPublished('ivypublishingtest-0.1.0-all.jar.sha1')
        !fileWasPublished('ivypublishingtest-0.1.0-all.jar.sha256')
        !fileWasPublished('ivypublishingtest-0.1.0-all.jar.sha512')

        fileWasPublished('ivypublishingtest-0.1.0.module')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha1')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha256')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha512')

        fileWasPublished('ivy-0.1.0.xml')
        fileWasPublished('ivy-0.1.0.xml.sha1')
        fileWasPublished('ivy-0.1.0.xml.sha256')
        fileWasPublished('ivy-0.1.0.xml.sha512')
    }

    def 'publish shadow jar with proper Ivy descriptor - with classifier'() {
        setup:
        buildFile << """
            jar {
              enabled = false // this configuration is used to produce only the shadowed jar
            }
            
            shadowJar {
               archiveClassifier.set('all') // this configuration is used to produce only the shadowed jar
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

        and:
        !fileWasPublished('ivypublishingtest-0.1.0.jar')
        !fileWasPublished('ivypublishingtest-0.1.0.jar.sha1')
        !fileWasPublished('ivypublishingtest-0.1.0.jar.sha256')
        !fileWasPublished('ivypublishingtest-0.1.0.jar.sha512')

        fileWasPublished('ivypublishingtest-0.1.0-all.jar')
        fileWasPublished('ivypublishingtest-0.1.0-all.jar.sha1')
        fileWasPublished('ivypublishingtest-0.1.0-all.jar.sha256')
        fileWasPublished('ivypublishingtest-0.1.0-all.jar.sha512')

        fileWasPublished('ivypublishingtest-0.1.0.module')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha1')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha256')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha512')

        fileWasPublished('ivy-0.1.0.xml')
        fileWasPublished('ivy-0.1.0.xml.sha1')
        fileWasPublished('ivy-0.1.0.xml.sha256')
        fileWasPublished('ivy-0.1.0.xml.sha512')
    }

    def 'publish shadow jar with proper Ivy descriptor - no classifier - manipulate xml'() {
        setup:
        buildFile << """
            jar {
              enabled = false // this configuration is used to produce only the shadowed jar
            }
            
            shadowJar {
                archiveClassifier.set(null) // this configuration is used to produce only the shadowed jar
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

        and:
        fileWasPublished('ivypublishingtest-0.1.0.jar')
        fileWasPublished('ivypublishingtest-0.1.0.jar.sha1')
        fileWasPublished('ivypublishingtest-0.1.0.jar.sha256')
        fileWasPublished('ivypublishingtest-0.1.0.jar.sha512')

        !fileWasPublished('ivypublishingtest-0.1.0-shadow.jar')
        !fileWasPublished('ivypublishingtest-0.1.0-shadow.jar.sha1')
        !fileWasPublished('ivypublishingtest-0.1.0-shadow.jar.sha256')
        !fileWasPublished('ivypublishingtest-0.1.0-shadow.jar.sha512')

        fileWasPublished('ivypublishingtest-0.1.0.module')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha1')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha256')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha512')

        fileWasPublished('ivy-0.1.0.xml')
        fileWasPublished('ivy-0.1.0.xml.sha1')
        fileWasPublished('ivy-0.1.0.xml.sha256')
        fileWasPublished('ivy-0.1.0.xml.sha512')
    }

    @Unroll
    def 'publish shadow jar with proper Ivy descriptor - with classifier and jar enabled - manipulate xml, shadow-jar version: #shadowJar'() {
        setup:
        buildFile.text = buildFile.text.replace("id \"com.github.johnrengelman.shadow\" version \"6.0.0\"",
                "id \"com.github.johnrengelman.shadow\" version \"$shadowJar\"")
        buildFile << """
            shadowJar {
               archiveClassifier.set('shadow') // this configuration is used to produce only the shadowed jar
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
        artifact.@conf == 'compile,runtime'

        def shadowartifact = root.publications.artifact[1]
        shadowartifact.@name == 'ivypublishingtest'
        shadowartifact.@type == 'jar'
        shadowartifact.@conf == 'shadowRuntimeElements'

        def desc = root
                .declareNamespace([nebula: 'http://netflix.com/build'])
                .info[0].description[0]
        desc.children().size() > 1
        desc.'nebula:Implementation_Version' == '0.1.0'
        desc.'nebula:Implementation_Title' == 'test.nebula#ivypublishingtest;0.1.0'
        desc.'nebula:Module_Email' == 'nebula@example.test'

        and:
        !findDependency('com.google.guava', 'guava')

        and:
        fileWasPublished('ivypublishingtest-0.1.0.jar')
        fileWasPublished('ivypublishingtest-0.1.0.jar.sha1')
        fileWasPublished('ivypublishingtest-0.1.0.jar.sha256')
        fileWasPublished('ivypublishingtest-0.1.0.jar.sha512')

        fileWasPublished('ivypublishingtest-0.1.0-shadow.jar')
        fileWasPublished('ivypublishingtest-0.1.0-shadow.jar.sha1')
        fileWasPublished('ivypublishingtest-0.1.0-shadow.jar.sha256')
        fileWasPublished('ivypublishingtest-0.1.0-shadow.jar.sha512')

        fileWasPublished('ivypublishingtest-0.1.0.module')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha1')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha256')
        fileWasPublished('ivypublishingtest-0.1.0.module.sha512')

        fileWasPublished('ivy-0.1.0.xml')
        fileWasPublished('ivy-0.1.0.xml.sha1')
        fileWasPublished('ivy-0.1.0.xml.sha256')
        fileWasPublished('ivy-0.1.0.xml.sha512')

        where:
        shadowJar << ['5.2.0', '6.0.0']
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

    private boolean fileWasPublished(String fileName, String path = 'build/distIvy/test.nebula/ivypublishingtest/0.1.0/') {
        return new File(projectDir, path + fileName).exists()
    }
}
