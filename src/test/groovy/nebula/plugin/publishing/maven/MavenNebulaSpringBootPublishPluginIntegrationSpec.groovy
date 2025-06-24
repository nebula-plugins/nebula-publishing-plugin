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

import groovy.xml.XmlSlurper
import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import nebula.plugin.publishing.publications.SpringBootJarPlugin
import spock.lang.IgnoreIf
import spock.lang.Subject

@Subject(SpringBootJarPlugin)
@IgnoreIf({ !jvm.isJava17Compatible() })
class MavenNebulaSpringBootPublishPluginIntegrationSpec  extends BaseIntegrationTestKitSpec {
    def setup() {
        keepFiles = true

        // Because Spring Boot 2.x uses project.conventions
        System.setProperty('ignoreDeprecations', 'true')
        // spring dependency management does not support config cache. More in https://github.com/spring-gradle-plugins/dependency-management-plugin/issues/312
        disableConfigurationCache()
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.maven-publish'
                id 'org.springframework.boot' version '3.5.3'
                id 'io.spring.dependency-management' version '1.+'
                id 'java'
                id "com.netflix.nebula.info" version "12.1.6"
                id "com.netflix.nebula.contacts" version "7.0.1"
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
                implementation 'org.springframework.boot:spring-boot-starter-web'
                runtimeOnly 'org.postgresql:postgresql'
                testImplementation('org.springframework.boot:spring-boot-starter-test') {
                exclude group: 'org.junit.vintage', module: 'junit-vintage-engine'
                }
            }
            
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

    def 'publish spring boot jar with proper POM'() {
        setup:
        writeJavaSourceFile("""
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

        @SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}

""")
        when:
        def result = runTasks('bootJar', 'publishNebulaPublicationToTestLocalRepository')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.version == '0.1.0'
        pom.developers.developer[0].name == 'Nebula'
        pom.properties.nebula_Module_Owner == 'nebula@example.test'
        pom.url != null

        when:
        def dependencies = pom.dependencies.dependency
        def spring = dependencies.find { it.artifactId == 'spring-boot-starter-web' }

        then:
        spring.version == '3.5.3'

        when:
        def jar = new File(projectDir, "build/libs/mavenpublishingtest-0.1.0.jar")

        then:
        jar.exists()
    }


}
