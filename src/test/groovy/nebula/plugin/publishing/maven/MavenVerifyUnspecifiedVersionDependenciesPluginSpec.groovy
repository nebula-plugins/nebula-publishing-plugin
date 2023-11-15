package nebula.plugin.publishing.maven

import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class MavenVerifyUnspecifiedVersionDependenciesPluginSpec extends BaseIntegrationTestKitSpec {
    File publishDir

    def setup() {
        settingsFile << '''\
            rootProject.name = 'resolvedmaventest'
            '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test/nebula/resolvedmaventest/0.1.0')
    }

    def 'Fails build if maven dependency version is unespecified'() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.maven-resolved-dependencies'
                id 'com.netflix.nebula.maven-nebula-publish'
                id 'com.netflix.nebula.maven-verify-unspecified-version-dependencies'
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


        def graph = new DependencyGraphBuilder().addModule('test.resolved:b:unspecified').build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories { maven { url '${mavenrepo.absolutePath}' } }

            dependencies {
                implementation 'test.resolved:b:unspecified'
            }

            """.stripIndent()

        when:
        def result = runTasksAndFail('publishNebulaPublicationToTestLocalRepository')

        then:
        result.output.contains('Dependency test.resolved:b has an invalid version: unspecified. This publication is invalid')
    }
}