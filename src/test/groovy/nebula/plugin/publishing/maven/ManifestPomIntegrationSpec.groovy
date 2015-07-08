package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec

class ManifestPomIntegrationSpec extends IntegrationSpec {
    def setup() {
        buildFile << """\
            ${applyPlugin(ManifestPomPlugin)}

            version = '0.1.0'
            group = 'test.nebula'
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'manifestpomtest'
        '''.stripIndent()
    }

    def 'manifest created'() {
        buildFile << '''\
            apply plugin: 'java'
            apply plugin: 'info'
        '''

        when:
        runTasksSuccessfully('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.properties.children().size() > 1
        pom.properties.'nebula_Implementation_Version' == '0.1.0'
        pom.properties.'nebula_Implementation_Title' == 'test.nebula#manifestpomtest;0.1.0'
    }
}
