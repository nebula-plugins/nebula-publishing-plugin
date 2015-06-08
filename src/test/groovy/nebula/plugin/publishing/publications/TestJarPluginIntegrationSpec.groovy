package nebula.plugin.publishing.publications

import nebula.plugin.publishing.maven.MavenPublishingPlugin
import nebula.test.IntegrationSpec

class TestJarPluginIntegrationSpec extends IntegrationSpec {
    File publishDir
    File unzipDir

    def setup() {
        buildFile << """\
            ${applyPlugin(MavenPublishingPlugin)}
            ${applyPlugin(TestJarPlugin)}

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
                def zipFile = file('testrepo/test/nebula/maventest/0.1.0/maventest-0.1.0-tests.jar')
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

    def 'create test jar'() {
        buildFile << '''\
            apply plugin: 'java'

            repositories { jcenter() }
            dependencies {
                testCompile 'junit:junit:4.11'
            }
        '''.stripIndent()

        writeHelloWorld('example')
        writeTest('src/test/java/', 'example', false)

        when:
        runTasksSuccessfully('unzip')

        then:
        def exampleTest = new File(unzipDir, 'example/HelloWorldTest.class')
        exampleTest.exists()
    }
}
