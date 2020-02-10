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

import nebula.test.IntegrationTestKitSpec

class MavenNebulaShadowJarPublishPluginIntegrationSpec extends IntegrationTestKitSpec {
    def setup() {
        debug = true
        keepFiles = true
        buildFile << """\
            plugins {
                id 'nebula.maven-publish'
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
                    maven {
                        name = 'testLocal'
                        url = 'testrepo'
                    }
                }
            }

            jar.dependsOn shadowJar // this configuration is used to produce only the shadowed jar

        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'mavenpublishingtest'
        '''.stripIndent()
    }

    def 'publish shadow jar with proper POM - no classifier'() {
        setup:
        buildFile << """
            jar {
              enabled = false // this configuration is used to produce only the shadowed jar
            }

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
        def result = runTasks('shadowJar', 'publishNebulaPublicationToTestLocalRepository')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.version == '0.1.0'
        pom.developers.developer[0].name == 'Nebula'
        pom.properties.nebula_Module_Owner == 'nebula@example.test'
        pom.url != null

        when:
        def dependencies = pom.dependencies.dependency
        def guava = dependencies.find { it.artifactId == 'guava' }

        then:
        guava.version == '19.0'

        and:
        fileWasPublished('mavenpublishingtest-0.1.0.jar')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.md5')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.sha512')

        !fileWasPublished('mavenpublishingtest-0.1.0-all.jar')
        !fileWasPublished('mavenpublishingtest-0.1.0-all.jar.md5')
        !fileWasPublished('mavenpublishingtest-0.1.0-all.jar.sha1')
        !fileWasPublished('mavenpublishingtest-0.1.0-all.jar.sha256')
        !fileWasPublished('mavenpublishingtest-0.1.0-all.jar.sha512')

        fileWasPublished('mavenpublishingtest-0.1.0.module')
        fileWasPublished('mavenpublishingtest-0.1.0.module.md5')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha512')

        fileWasPublished('mavenpublishingtest-0.1.0.pom')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.md5')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha512')
    }


    def 'publish shadow jar with proper POM - with classifier'() {
        setup:
        buildFile << """            
            jar {
              enabled = false // this configuration is used to produce only the shadowed jar
            }

            shadowJar {
                classifier 'all' 
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
        def result = runTasks('shadowJar', 'publishNebulaPublicationToTestLocalRepository')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.version == '0.1.0'
        pom.developers.developer[0].name == 'Nebula'
        pom.properties.nebula_Module_Owner == 'nebula@example.test'
        pom.url != null

        when:
        def dependencies = pom.dependencies.dependency
        def guava = dependencies.find { it.artifactId == 'guava' }

        then:
        guava.version == '19.0'

        and:
        !fileWasPublished('mavenpublishingtest-0.1.0.jar')
        !fileWasPublished('mavenpublishingtest-0.1.0.jar.md5')
        !fileWasPublished('mavenpublishingtest-0.1.0.jar.sha1')
        !fileWasPublished('mavenpublishingtest-0.1.0.jar.sha256')
        !fileWasPublished('mavenpublishingtest-0.1.0.jar.sha512')

        fileWasPublished('mavenpublishingtest-0.1.0-all.jar')
        fileWasPublished('mavenpublishingtest-0.1.0-all.jar.md5')
        fileWasPublished('mavenpublishingtest-0.1.0-all.jar.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0-all.jar.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0-all.jar.sha512')

        fileWasPublished('mavenpublishingtest-0.1.0.module')
        fileWasPublished('mavenpublishingtest-0.1.0.module.md5')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha512')

        fileWasPublished('mavenpublishingtest-0.1.0.pom')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.md5')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha512')
    }

    def 'publish shadow jar with proper POM - no classifier - manipulate xml'() {
        setup:
        buildFile << """
            jar {
              enabled = false // this configuration is used to produce only the shadowed jar
            }

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
        def result = runTasks('shadowJar', 'publishNebulaPublicationToTestLocalRepository')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.version == '0.1.0'
        pom.developers.developer[0].name == 'Nebula'
        pom.properties.nebula_Module_Owner == 'nebula@example.test'
        pom.url != null

        when:
        def dependencies = pom.dependencies.dependency
        def guava = dependencies.find { it.artifactId == 'guava' }

        then:
        !guava

        and:
        fileWasPublished('mavenpublishingtest-0.1.0.jar')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.md5')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.sha512')

        !fileWasPublished('mavenpublishingtest-0.1.0-shadow.jar')
        !fileWasPublished('mavenpublishingtest-0.1.0-shadow.jar.md5')
        !fileWasPublished('mavenpublishingtest-0.1.0-shadow.jar.sha1')
        !fileWasPublished('mavenpublishingtest-0.1.0-shadow.jar.sha256')
        !fileWasPublished('mavenpublishingtest-0.1.0-shadow.jar.sha512')

        fileWasPublished('mavenpublishingtest-0.1.0.module')
        fileWasPublished('mavenpublishingtest-0.1.0.module.md5')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha512')

        fileWasPublished('mavenpublishingtest-0.1.0.pom')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.md5')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha512')
    }

    def 'publish shadow jar with proper POM - with classifier and jar enabled - manipulate xml'() {
        setup:
        buildFile << """
            shadowJar {
                classifier 'shadow' 
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
        def result = runTasks('shadowJar', 'publishNebulaPublicationToTestLocalRepository')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.version == '0.1.0'
        pom.developers.developer[0].name == 'Nebula'
        pom.properties.nebula_Module_Owner == 'nebula@example.test'
        pom.url != null

        when:
        def dependencies = pom.dependencies.dependency
        def guava = dependencies.find { it.artifactId == 'guava' }

        then:
        !guava

        and:
        fileWasPublished('mavenpublishingtest-0.1.0.jar')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.md5')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0.jar.sha512')

        fileWasPublished('mavenpublishingtest-0.1.0-shadow.jar')
        fileWasPublished('mavenpublishingtest-0.1.0-shadow.jar.md5')
        fileWasPublished('mavenpublishingtest-0.1.0-shadow.jar.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0-shadow.jar.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0-shadow.jar.sha512')

        fileWasPublished('mavenpublishingtest-0.1.0.module')
        fileWasPublished('mavenpublishingtest-0.1.0.module.md5')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0.module.sha512')

        fileWasPublished('mavenpublishingtest-0.1.0.pom')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.md5')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha1')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha256')
        fileWasPublished('mavenpublishingtest-0.1.0.pom.sha512')
    }

    private boolean fileWasPublished(String fileName, String path = 'testrepo/test/nebula/mavenpublishingtest/0.1.0/') {
        return new File(projectDir, path + fileName).exists()
    }
}
