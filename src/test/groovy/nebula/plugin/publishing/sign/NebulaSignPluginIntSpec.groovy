package nebula.plugin.publishing.sign

import nebula.test.IntegrationSpec
import spock.lang.Ignore

class NebulaSignPluginIntSpec extends IntegrationSpec {

    @Ignore('GRADLE-2999 We cant publish locally AND sign')
    def 'applies'() {
        writeHelloWorld('nebula.hello')
        directory('build/gpg')
        copyResources('nebula/plugin/publishing/sign/test_secring.gpg', 'build/gpg/test_sec.gpg')
        buildFile << '''
            ext.'signing.keyId' = '21239086'
            ext.'signing.password' = ''
            ext.'signing.secretKeyRingFile' = 'build/gpg/test_sec.gpg'
            version='1.0.0'
            group='test'

            apply plugin: 'java'
            apply plugin: 'nebula-sign'
            apply plugin: 'nebula-source-jar'
            apply plugin: 'nebula-maven-publishing'
            apply plugin: 'nebula-maven-distribute'

        '''.stripIndent()

        when:
        runTasksSuccessfully('distribute')

        then:
        fileExists('distMaven/test/applies/1.0.0/applies-1.0.0.jar')
        fileExists('distMaven/test/applies/1.0.0/applies-1.0.0.jar.asc')
        fileExists('distMaven/test/applies/1.0.0/applies-1.0.0-sources.jar')
        fileExists('distMaven/test/applies/1.0.0/applies-1.0.0-sources.jar.asc')
        fileExists('distMaven/test/applies/1.0.0/applies-1.0.0.pom')
        fileExists('distMaven/test/applies/1.0.0/applies-1.0.0.pom.asc')
    }
}
