package nebula.plugin.publishing.publications

import nebula.plugin.publishing.maven.MavenPublishingPlugin
import nebula.test.IntegrationSpec

class JavadocJarPluginIntegrationSpec extends IntegrationSpec {
    File publishDir
    File unzipDir

    def setup() {
        buildFile << """\
            ${applyPlugin(MavenPublishingPlugin)}
            ${applyPlugin(JavadocJarPlugin)}

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

            task unzip(type: Copy) {
                def zipFile = file('testrepo/test/nebula/maventest/0.1.0/maventest-0.1.0-javadoc.jar')
                def outputDir = file('unpacked')

                from zipTree(zipFile)
                into outputDir
            }

            unzip.dependsOn 'publishNebulaPublicationToTestLocalRepository'
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'maventest'
        '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test/nebula/maventest/0.1.0')
        unzipDir = new File(projectDir, 'unpacked')
    }

    def 'javadoc jar is created'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0-javadoc.jar').exists()
    }
}
