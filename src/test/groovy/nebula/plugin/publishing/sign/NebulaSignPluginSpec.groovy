package nebula.plugin.publishing.sign

import nebula.plugin.publishing.NebulaSourceJarPlugin
import nebula.plugin.publishing.component.CustomComponentPlugin
import nebula.plugin.publishing.component.CustomSoftwareComponent
import nebula.test.ProjectSpec
import org.apache.commons.io.FileUtils
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningPlugin

class NebulaSignPluginSpec extends ProjectSpec {
    def 'applies plugin'() {
        when:
        project.apply plugin: 'nebula-sign'
        project.apply plugin: 'java'

        then:
        noExceptionThrown()
        project.plugins.hasPlugin(SigningPlugin)
    }

    def 'apply plugin with properties'() {
        when:
        URL resource = getClass().classLoader.getResource('nebula/plugin/publishing/sign/test_secring.gpg')
        if (resource == null) {
            throw new RuntimeException("Could not find classpath resource: $srcDir")
        }

        FileUtils.copyFileToDirectory(new File(resource.toURI()), projectDir)
        project.ext.setProperty('signing.keyId', '21239086')
        project.ext.setProperty('signing.password', '')
        project.ext.setProperty('signing.secretKeyRingFile', 'test_secring.gpg')
        project.plugins.apply(NebulaSignPlugin)
        project.plugins.apply(JavaPlugin)

        then:
        Sign signJarTask = project.tasks.getByName('signJars')
        signJarTask.signatures.size() > 0

        when:
        project.evaluate()

        then:
        noExceptionThrown()
    }
}
