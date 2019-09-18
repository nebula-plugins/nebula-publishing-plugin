package nebula.plugin.publishing.maven


import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class MavenRemoveInvalidDependenciesPluginSpec extends IntegrationSpec {
    File publishDir

    def setup() {
        settingsFile << '''\
            rootProject.name = 'resolvedmaventest'
            '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test/nebula/resolvedmaventest/0.1.0')
    }

    def 'publishes maven descriptor without platform dependency'() {
        buildFile << """\
            ${applyPlugin(MavenResolvedDependenciesPlugin)}
            ${applyPlugin(MavenNebulaPublishPlugin)}
            ${applyPlugin(MavenRemoveInvalidDependenciesPlugin)}

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

                       repositories { maven { url '${mavenrepo.absolutePath}' } }


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