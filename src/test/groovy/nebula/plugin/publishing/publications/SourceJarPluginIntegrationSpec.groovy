package nebula.plugin.publishing.publications

import nebula.plugin.publishing.maven.MavenPublishingPlugin
import nebula.test.IntegrationSpec

class SourceJarPluginIntegrationSpec extends IntegrationSpec {
    File publishDir
    File unzipDir

    def setup() {
        buildFile << """\
            ${applyPlugin(MavenPublishingPlugin)}
            ${applyPlugin(SourceJarPlugin)}

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
                def zipFile = file('testrepo/test/nebula/maventest/0.1.0/maventest-0.1.0-sources.jar')
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

    def 'creates a source jar'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0-sources.jar').exists()
    }

    def 'source jar contains java sources'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        writeHelloWorld('example')

        when:
        runTasksSuccessfully('unzip')

        then:
        def helloWorld = new File(unzipDir, 'example/HelloWorld.java')
        helloWorld.exists()
        helloWorld.text.contains 'public class HelloWorld'
    }

    def 'source jar contains groovy sources'() {
        buildFile << '''\
            apply plugin: 'groovy'

            dependencies {
                compile localGroovy()
            }
        '''.stripIndent()

        def dir = new File(projectDir, 'src/main/groovy/example')
        dir.mkdirs()
        def example = new File(dir, 'HelloWorld.groovy')
        example.text = '''\
            package example

            class HelloWorld {
                static void main(String[] args) {
                    println 'Hello world'
                }
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('unzip')

        then:
        def helloWorld = new File(unzipDir, 'example/HelloWorld.groovy')
        helloWorld.exists()
        helloWorld.text.contains 'class HelloWorld'
    }
}
