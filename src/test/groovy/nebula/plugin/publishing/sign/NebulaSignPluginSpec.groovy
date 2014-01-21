package nebula.plugin.publishing.sign

import nebula.plugin.publishing.maven.NebulaMavenPublishingPlugin
import nebula.test.ProjectSpec
import org.apache.commons.io.FileUtils
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningPlugin
import spock.lang.Ignore

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
        copyKeyRing()
        project.ext.setProperty('signing.keyId', keyId)
        project.ext.setProperty('signing.password', keyPassword)
        project.ext.setProperty('signing.secretKeyRingFile', keyRingFilename)
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

    final String keyId = '21239086'
    final String keyPassword = ''
    final String keyRingFilename = 'test_secring.gpg'

    @Ignore
    def copyKeyRing() {
        URL resource = getClass().classLoader.getResource("nebula/plugin/publishing/sign/${keyRingFilename}")
        if (resource == null) {
            throw new RuntimeException("Could not find classpath resource: $srcDir")
        }

        FileUtils.copyFileToDirectory(new File(resource.toURI()), projectDir)
    }

    def 'primary artifact'() {
        when:
        copyKeyRing()
        project.ext.setProperty('signing.keyId', keyId)
        project.ext.setProperty('signing.password', keyPassword)
        project.ext.setProperty('signing.secretKeyRingFile', keyRingFilename)
        project.group = 'test'
        project.plugins.apply(NebulaMavenPublishingPlugin)
        project.plugins.apply(NebulaSignPlugin)
        project.apply plugin: 'java'
        project.dependencies {
            compile 'asm:asm:3.1'
        }
        project.evaluate()
        GenerateMavenPom generateTask = project.tasks.getByName('generatePomFileForMavenJavaPublication')
        generateTask.doGenerate()

        then:
        MavenPublication mavenJava = project.publishing.publications.getByName('mavenJava')
        mavenJava.groupId == 'test'
        mavenJava.artifacts.size() == 3 // .jar .jar.asc .pom

        def pom = generateTask.destination.text
        pom.contains("<packaging>asm</packaging>") || !pom.contains('<packaging>')
    }
}
