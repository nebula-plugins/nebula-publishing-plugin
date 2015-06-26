package nebula.plugin.publishing.maven.license

import nebula.test.IntegrationSpec

class ApacheLicensePomIntegrationSpec extends IntegrationSpec {
    def 'add license works'() {
        buildFile << """\
            ${applyPlugin(ApacheLicensePomPlugin)}

            version = '0.1.0'
            group = 'test.nebula'
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'apachelicensepomtest'
        '''.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.licenses.license.name.text() == 'The Apache Software License, Version 2.0'
        pom.licenses.license.url.text() == 'http://www.apache.org/licenses/LICENSE-2.0.txt'
        pom.licenses.license.distribution.text() == 'repo'
    }
}
