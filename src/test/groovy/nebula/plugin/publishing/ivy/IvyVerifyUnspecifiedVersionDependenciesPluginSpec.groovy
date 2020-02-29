package nebula.plugin.publishing.ivy

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class IvyVerifyUnspecifiedVersionDependenciesPluginSpec extends IntegrationSpec {
    File publishDir

    def setup() {
        settingsFile << '''\
            rootProject.name = 'removeinvaliddependenciestest'
            '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test.nebula/removeinvaliddependenciestest/0.1.0')
    }

    def 'Fails build if ivy dependency version is unespecified'() {
        buildFile << """\
            ${applyPlugin(IvyResolvedDependenciesPlugin)}
            ${applyPlugin(IvyNebulaPublishPlugin)}
            ${applyPlugin(IvyVerifyUnspecifiedVersionDependenciesPlugin)}

            version = '0.1.0'
            group = 'test.nebula'

            publishing {
                repositories {
                    ivy {
                        name = 'testLocal'
                        url = 'testrepo'
                    }
                }
            }
            """.stripIndent()


        def graph = new DependencyGraphBuilder().addModule('test.resolved:b:unspecified').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")

        generator.generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ${generator.ivyRepositoryBlock}
                jcenter()
            }

            dependencies {
                implementation 'test.resolved:b:unspecified'
            }

            """.stripIndent()

        when:
        def result = runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        result.standardError.contains('Dependency test.resolved:b has an invalid version: unspecified. This publication is invalid')
    }
}