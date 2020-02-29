package nebula.plugin.publishing.maven


import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class MavenVerifyUnspecifiedVersionDependenciesPluginSpec extends IntegrationSpec {
    File publishDir

    def setup() {
        settingsFile << '''\
            rootProject.name = 'resolvedmaventest'
            '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test/nebula/resolvedmaventest/0.1.0')
    }

    def 'Fails build if maven dependency version is unespecified'() {
        buildFile << """\
            ${applyPlugin(MavenResolvedDependenciesPlugin)}
            ${applyPlugin(MavenNebulaPublishPlugin)}
            ${applyPlugin(MavenVerifyUnspecifiedVersionDependenciesPlugin)}

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
        def result = runTasks('publishNebulaPublicationToTestLocalRepository')

        then:
        result.standardError.contains('Dependency test.resolved:b has an invalid version: unspecified. This publication is invalid')
    }
}