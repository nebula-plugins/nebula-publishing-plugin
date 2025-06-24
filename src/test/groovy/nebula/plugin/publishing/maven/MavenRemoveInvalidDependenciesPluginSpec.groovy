package nebula.plugin.publishing.maven

import groovy.xml.XmlSlurper
import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import spock.lang.Subject

@Subject(MavenRemoveInvalidDependenciesPlugin)
class MavenRemoveInvalidDependenciesPluginSpec extends BaseIntegrationTestKitSpec {
    File publishDir

    def setup() {
        settingsFile << '''\
            rootProject.name = 'resolvedmaventest'
            '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test/nebula/resolvedmaventest/0.1.0')
    }

    def 'publishes maven descriptor without platform dependency'() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.maven-resolved-dependencies'
                id 'com.netflix.nebula.maven-nebula-publish'
                id 'com.netflix.nebula.maven-remove-invalid-dependencies'
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


        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule('test.resolved:a:1.1.0')
                .addModule('test.resolved:b:1.1.0').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

                       repositories { maven { url = '${mavenrepo.absolutePath}' } }


            dependencies {
                implementation 'test.resolved:a'
                implementation 'test.resolved:b:1.+'
                modules {
                    module("test.resolved:a") {
                        replacedBy("test.resolved:b", "b is better")
                    }
                }
            }

            """.stripIndent()

        when:
        runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        !a

        def b = findDependency('b')
        b.version == '1.1.0'
    }

    def findDependency(String artifactId) {
        def root = new XmlSlurper().parseText(new File(publishDir, 'resolvedmaventest-0.1.0.pom').text)
        def d = root.dependencies.dependency.find {
            it.artifactId == artifactId
        }
        return d
    }

}