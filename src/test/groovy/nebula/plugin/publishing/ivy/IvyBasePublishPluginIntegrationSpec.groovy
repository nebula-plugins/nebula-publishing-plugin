package nebula.plugin.publishing.ivy

import nebula.plugin.testkit.IntegrationHelperSpec

class IvyBasePublishPluginIntegrationSpec extends IntegrationHelperSpec {
    File publishDir

    def setup() {
        keepFiles = true

        buildFile << """\
            apply plugin: 'nebula.ivy-base-publish'

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

        settingsFile << '''\
            rootProject.name = 'ivytest'
        '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test.nebula/ivytest/0.1.0')
    }

    def 'verify ivy.xml is correct'() {
        buildFile << '''\
            apply plugin: 'java'

            description = 'test description'
        '''.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parse(new File(publishDir, 'ivy-0.1.0.xml'))
        root.info.@organisation == 'test.nebula'
        root.info.@module == 'ivytest'
        root.info.@revision == '0.1.0'
        root.info.description == 'test description'

        def artifact = root.publications.artifact[0]
        artifact.@name == 'ivytest'
        artifact.@type == 'jar'
        artifact.@ext == 'jar'
        artifact.@conf == 'runtime'
    }
}
