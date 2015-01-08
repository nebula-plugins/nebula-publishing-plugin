package nebula.plugin.publishing.manifest

import nebula.test.IntegrationSpec

class NebulaPublishManifestPluginLauncherSpec extends IntegrationSpec {

    String mavenLocal = "${System.env['HOME']}/.m2/repository"

    def 'published pom contains a collected property'() {
        given: 'a java project applying the metadata plugin'
        settingsFile = new File(projectDir, 'settings.gradle')
        settingsFile.text = "rootProject.name='world'"
        writeHelloWorld('nebula.hello')
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'info'
            apply plugin: 'nebula-publishing'
            apply plugin: 'nebula-publish-manifest'

            group = 'nebula.hello'
            version = '1.0'

            repositories { jcenter() }

            dependencies {
                compile 'asm:asm:3.1'
            }
        '''.stripIndent()

        when: 'the artifacts are built and published'
        runTasksSuccessfully('publishToMavenLocal')

        then: 'publishes a pom file'
        def pomFile = new File("$mavenLocal/nebula/hello/world/1.0/world-1.0.pom")
        pomFile.exists()

        and: 'the published pom contains a collected value'
        def pom = new XmlSlurper().parseText(pomFile.text)
        pom.properties.'nebula_Implementation_Version' == '1.0'
        pom.properties.'nebula_Implementation_Title' == 'nebula.hello#world;1.0'
    }

    def 'multiproject with interproject dependency'() {
        buildFile << """
            subprojects {
                apply plugin: 'java'
                apply plugin: 'info'
                apply plugin: 'nebula-publishing'
                apply plugin: 'nebula-publish-manifest'

                group = 'nebula.hello'
                version = '1.0'
            }
        """.stripIndent()

        addSubproject('sub1', '// hello')
        addSubproject('sub2', '''\
            apply plugin: 'war'

            dependencies {
                compile project(':sub1')
            }
        '''.stripIndent())

        when: 'the artifacts are built and published'
        runTasksSuccessfully('publishToMavenLocal')

        then: 'publishes a pom file'
        def pomFile = new File("$mavenLocal/nebula/hello/sub2/1.0/sub2-1.0.pom")
        pomFile.exists()

    }
}
