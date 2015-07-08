package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec


class ScmPomIntegrationSpec extends IntegrationSpec {
    def setup() {
        buildFile << """\
            ${applyPlugin(ScmPomPlugin)}

            version = '0.1.0'
            group = 'test.nebula'
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'scmpomtest'
        '''.stripIndent()
    }

    def 'scm info is present in pom'() {
        buildFile << """
            apply plugin: 'nebula.info'
        """.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.scm.url.text().endsWith('.git')
        pom.url.text().startsWith('https://github.com/')
        pom.url.text().endsWith('nebula-publishing-plugin')
    }
}
