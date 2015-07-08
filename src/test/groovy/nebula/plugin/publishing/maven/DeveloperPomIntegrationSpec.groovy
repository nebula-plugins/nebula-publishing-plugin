package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec

class DeveloperPomIntegrationSpec extends IntegrationSpec {
    def setup() {
        buildFile << """\
            ${applyPlugin(DeveloperPomPlugin)}

            version = '0.1.0'
            group = 'test.nebula'
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'developerpomtest'
        '''.stripIndent()
    }

    def 'take info from contacts plugin and place in pom'() {
        buildFile << '''\
            apply plugin: 'nebula.contacts'

            contacts {
                'nebula@example.test' {
                    moniker 'Example Nebula'
                    github 'nebula-plugins'
                }
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForNebulaPublication')

        then:
        def pomFile = new File(projectDir, 'build/publications/nebula/pom-default.xml')
        pomFile.exists()

        def pom = new XmlSlurper().parse(pomFile)

        def devs = pom.developers.developer
        devs.size() == 1
        devs[0].name.text() == 'Example Nebula'
        devs[0].email.text() == 'nebula@example.test'
        devs[0].id.text() == 'nebula-plugins'
    }

    def 'multiple contacts'() {
        buildFile << '''\
            apply plugin: 'nebula.contacts'

            contacts {
                'nebula1@example.test' {
                    moniker 'Example Nebula 1'
                }
                'nebula2@example.test' {
                    moniker 'Example Nebula 2'
                }
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForNebulaPublication')

        then:
        def pomFile = new File(projectDir, 'build/publications/nebula/pom-default.xml')
        def pom = new XmlSlurper().parse(pomFile)

        def devs = pom.developers.developer
        devs.size() == 2
        [devs[0].email.text(), devs[1].email.text()].containsAll(['nebula1@example.test', 'nebula2@example.test'])
    }

    def 'single role is found in pom'() {
        buildFile << '''\
            apply plugin: 'nebula.contacts'

            contacts {
                'nebula@example.test' {
                    role 'developer'
                }
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        def roles = pom.developers.developer[0].roles.role
        roles.size() == 1
        roles[0].text() == 'developer'
    }

    def 'multiple roles found in pom for singular developer'() {
        buildFile << '''\
            apply plugin: 'nebula.contacts'

            contacts {
                'nebula@example.test' {
                    role 'developer'
                    role 'maintainer'
                }
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        def roles = pom.developers.developer[0].roles.role
        roles.size() == 2
        [roles[0].text(), roles[1].text()].containsAll(['developer', 'maintainer'])
    }
}
