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

import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import spock.lang.IgnoreIf

@IgnoreIf({ !jvm.isJava17Compatible() })
class IvyNebulaSpringBootPublishPluginIntegrationSpec extends BaseIntegrationTestKitSpec {
    def setup() {
        keepFiles = true
        // Because Spring Boot 2.x uses project.conventions
        System.setProperty('ignoreDeprecations', 'true')
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.ivy-publish'
                id 'org.springframework.boot' version '3.2.1'
                id 'io.spring.dependency-management' version '1.1.4'
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
                implementation 'org.springframework.boot:spring-boot-starter-web'
                runtimeOnly 'org.postgresql:postgresql'
                testImplementation('org.springframework.boot:spring-boot-starter-test') {
                exclude group: 'org.junit.vintage', module: 'junit-vintage-engine'
                }
            }
            
             publishing {
                repositories {
                    ivy {
                        name = 'distIvy'
                        url = project.file("\${project.layout.buildDirectory.getAsFile().get()}/distIvy").toURI().toURL()
                    }
                }
            }
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'ivypublishingtest'
        '''.stripIndent()
    }

    def 'publish spring boot jar with proper Ivy descriptor'() {
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
        def result = runTasks('bootJar', 'publishNebulaIvyPublicationToDistIvyRepository')

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
        assertDependency('org.springframework.boot', 'spring-boot-starter-web', '3.2.1', 'runtime->default')
        assertDependency('org.postgresql', 'postgresql', '42.6.0', 'runtime->default')

        when:
        def jar = new File(projectDir, "build/libs/ivypublishingtest-0.1.0.jar")

        then:
        jar.exists()
    }

    boolean assertDependency(String org, String name, String rev, String conf = null) {
        def dependencies = new XmlSlurper().parse(new File(projectDir, 'build/distIvy/test.nebula/ivypublishingtest/0.1.0/ivy-0.1.0.xml')).dependencies.dependency
        def found = dependencies.find { it.@name == name && it.@org == org }
        assert found
        assert found.@rev == rev
        assert !conf || found.@conf == conf
        found
    }


}
