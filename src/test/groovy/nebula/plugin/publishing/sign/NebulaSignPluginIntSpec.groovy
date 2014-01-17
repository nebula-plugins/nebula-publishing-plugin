package nebula.plugin.publishing.sign

import nebula.test.IntegrationSpec

class NebulaSignPluginIntSpec extends IntegrationSpec {
    // I just can't get this to work via the GradleLauncher. Command line works fine.
    /**
    def 'applies'() {
        writeHelloWorld('nebula.hello')
        directory('build/gpg')
        copyResources('nebula/plugin/publishing/sign/test_sec.gpg', 'build/gpg/test_sec.gpg')
        buildFile << '''
            ext.'signing.keyId' = '21239086'
            ext.'signing.password' = ''
            ext.'signing.secretKeyRingFile' = 'build/gpg/test_sec.gpg'
            version='1.0.0'
            apply plugin: 'signing'
            apply plugin: 'java'

configurations {
    jarholder
}
artifacts {
    jarholder tasks.jar
}
            signing {
                sign configurations.jarholder
            }

            //apply plugin: 'nebula-sign'
            //apply plugin: 'nebula-source-jar'
        '''.stripIndent()

        when:
        runTasksSuccessfully('build')
//        runTasksSuccessfully('preparePublish')

        then:
        fileExists('build/libs/applies-1.0.0.jar')
        fileExists('build/libs/applies-1.0.0.jar.asc')
        fileExists('build/libs/applies-1.0.0-sources.jar')
        fileExists('build/libs/applies-1.0.0-sources.jar.asc')
        fileExists('build/libs/applies-1.0.0.pom')
        fileExists('build/libs/applies-1.0.0.pom.asc')
    }
     */
}
