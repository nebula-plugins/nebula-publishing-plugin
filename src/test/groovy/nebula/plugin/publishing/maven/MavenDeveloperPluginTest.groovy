package nebula.plugin.publishing.maven

import groovy.xml.XmlSlurper
import nebula.test.dsl.GroovyTestProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import static nebula.test.dsl.TestKitAssertions.assertThat

class MavenDeveloperPluginTest {
    @TempDir
    File projectDir

    @Test
    void test_multiproject() {
        def runner = GroovyTestProjectBuilder.testProject(projectDir) {
            rootProject {
                plugins {
                    id("com.netflix.nebula.contacts")
                }
                rawBuildScript("""
contacts {
    'nebula@example.test' {
        moniker 'Example Nebula'
        github 'nebula-plugins'
    }
}
""")
            }
            subProject("sub1") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.contacts")
                    id 'com.netflix.nebula.maven-developer'
                    id 'com.netflix.nebula.maven-nebula-publish'
                }
            }
        }

        def result = runner.run(':sub1:generatePomFileForNebulaPublication')
        assertThat(result).hasNoDeprecationWarnings()
        def pomFile = new File(projectDir, 'sub1/build/publications/nebula/pom-default.xml')
        assertThat(pomFile).exists()

        def pom = new XmlSlurper().parse(pomFile)

        def devs = pom.developers.developer
        assertThat(devs.size()).isEqualTo(1)
        assertThat(devs[0].name.text()).isEqualTo('Example Nebula')
        assertThat(devs[0].email.text()).isEqualTo('nebula@example.test')
        assertThat(devs[0].id.text()).isEqualTo('nebula-plugins')
    }
}
