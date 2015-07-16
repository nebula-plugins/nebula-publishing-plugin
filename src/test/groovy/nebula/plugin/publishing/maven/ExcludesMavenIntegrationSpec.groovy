package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder

class ExcludesMavenIntegrationSpec extends IntegrationSpec {
    def setup() {
        buildFile << """\
            ${applyPlugin(MavenJavaPublishingPlugin)}

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

        settingsFile << '''\
            rootProject.name = 'excludesmaven'
        '''.stripIndent()
    }

    def 'add excludes to dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test:a:1.0.0')
                            .addDependency('test:b:2.0.0')
                            .addDependency('test:c:0.9.0')
                            .build())
                .addModule('test:b:1.9.2')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                compile('test:a:1.0.0') {
                    exclude group: 'test', module: 'b'
                }
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.dependencies.dependency[0].exclusions.exclusion.size() == 1
        def aExclusion = pom.dependencies.dependency[0].exclusions.exclusion[0]
        aExclusion.groupId == 'test'
        aExclusion.artifactId == 'b'
    }
}
