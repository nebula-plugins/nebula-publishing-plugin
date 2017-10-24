package nebula.plugin.publishing.ivy.interaction

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import spock.lang.Unroll

class IvyPublishRecommenderInteractionSpec extends IntegrationTestKitSpec {
    @Unroll
    def 'dependencies from recommendation plugin in #scope scope should be in ivy file'() {
        def graph = new DependencyGraphBuilder().addModule('test:foo:1.0.0').build()
        def generator = new GradleDependencyGenerator(graph, "$projectDir/repo")
        generator.generateTestMavenRepo()

        settingsFile.text = 'rootProject.name=\'mytest\''

        buildFile << """\
            plugins {
                id 'java'
                id 'nebula.ivy-publish'
                id 'nebula.dependency-recommender' version '5.0.0'
            }
            
            group = 'test.nebula'
            version = '0.1.0'
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }

            dependencyRecommendations {
               map recommendations: ['test:foo': '1.0.0']
            }

            dependencies {
                $scope 'test:foo'
            }

            publishing {
                repositories {
                    ivy {
                        name 'testlocal'
                        url 'build/testlocal'
                    }
                }
            }
            """.stripIndent()

        when:
        def results = runTasks('publishNebulaIvyPublicationToTestlocalRepository')

        then:
        def ivy = new XmlSlurper().parse(new File(projectDir, 'build/testlocal/test.nebula/mytest/0.1.0/ivy-0.1.0.xml'))
        ivy.dependencies.dependency.first().@rev == '1.0.0'

        where:
        scope << ['runtime', 'compile', 'compileOnly', 'runtimeOnly']

    }
}
