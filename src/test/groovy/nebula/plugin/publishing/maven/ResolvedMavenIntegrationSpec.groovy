package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec

class ResolvedMavenIntegrationSpec extends IntegrationSpec {
    File publishDir

    def setup() {
        buildFile << """\
            ${applyPlugin(MavenPublishingPlugin)}

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
            rootProject.name = 'resolvedmaventest'
        '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test/nebula/resolvedmaventest/0.1.0')
    }

    def ''() {

    }
}
