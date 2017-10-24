package nebula.plugin.publishing.ivy.interaction

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class IvyPublishRecommenderInteractionSpec extends IntegrationTestKitSpec {
    def 'dependencies in runtime provided by recommender are not put in ivy file'() {
        keepFiles = true
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
                runtime 'test:foo'
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
        ivy.dependencies.dependency[0].@rev == '1.0.0'
    }
}
