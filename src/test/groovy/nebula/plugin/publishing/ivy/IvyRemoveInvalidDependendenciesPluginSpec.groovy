package nebula.plugin.publishing.ivy

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class IvyRemoveInvalidDependendenciesPluginSpec extends IntegrationSpec {
    File publishDir

    def setup() {
        settingsFile << '''\
            rootProject.name = 'removeinvaliddependenciestest'
            '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test.nebula/removeinvaliddependenciestest/0.1.0')
    }

    def 'publishes ivy descriptor without platform dependency'() {
        buildFile << """\
            ${applyPlugin(IvyResolvedDependenciesPlugin)}
            ${applyPlugin(IvyNebulaPublishPlugin)}
            ${applyPlugin(IvyRemoveInvalidDependenciesPlugin)}

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


        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule('test.resolved:a:1.1.0')
                .addModule('test.resolved:b:1.1.0').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")

        generator.generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ${generator.ivyRepositoryBlock}
                mavenCentral()
            }

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
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        !a

        def b = findDependency('b')
        b.@rev == '1.1.0'
    }



    def findDependency(String module) {
        def root = new XmlSlurper().parseText(new File(publishDir, 'ivy-0.1.0.xml').text)
        def d = root.dependencies.dependency.find {
            it.@name == module
        }
        return d
    }

}