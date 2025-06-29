package nebula.plugin.publishing.ivy

import groovy.xml.XmlSlurper
import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import spock.lang.Subject

@Subject(IvyCompileOnlyPlugin)
class IvyCompileOnlyPluginIntegrationSpec extends BaseIntegrationTestKitSpec {
    File publishDir

    def setup() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.ivy-nebula-publish'
                id 'com.netflix.nebula.ivy-compile-only'
            }
            version = '0.1.0'
            group = 'test.nebula'
            repositories {
                mavenCentral()
            }
            
            publishing {
                repositories {
                    ivy {
                        name = 'testLocal'
                        url = 'testrepo'
                    }
                }
            }
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'ivytest'
        '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test.nebula/ivytest/0.1.0')
    }

    def 'verify ivy contains compileOnly dependencies'() {
        keepFiles = true
        buildFile << """\
            apply plugin: 'java'
            dependencies {
                compileOnly 'com.google.guava:guava:19.0'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'ivy-0.1.0.xml').text)
        def dependency = root.dependencies.dependency[0]
        dependency.@org == 'com.google.guava'
        dependency.@name == 'guava'
        dependency.@rev == '19.0'
        dependency.@conf == 'provided'
    }

    def 'verify ivy contains compileOnly dependencies together with global excludes'() {
        keepFiles = true

        buildFile << """\
            apply plugin: 'java'
            configurations.all {
                exclude group: 'org.slf4j', module: 'slf4j-api'
            }
            dependencies {
                compileOnly 'com.google.guava:guava:19.0'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'ivy-0.1.0.xml').text)
        def dependency = root.dependencies.dependency[0]
        dependency.@org == 'com.google.guava'
        dependency.@name == 'guava'
        dependency.@rev == '19.0'
        dependency.@conf == 'provided'
    }
}