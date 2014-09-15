package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec

class ResolvedMavenPluginIntSpec extends IntegrationSpec {

    def pomLocation = 'build/publications/mavenNebula/pom-default.xml'

    def 'produces pom after resolution'() {
        writeHelloWorld('nebula.hello')
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'nebula-maven-publishing'
            repositories { jcenter() }
            dependencies {
                compile 'asm:asm:3.1'
                compile('org.apache.httpcomponents:httpclient:4.3.1') {
                    exclude group: 'org.apache.httpcomponents', module: 'httpcore'
                    exclude module: 'commons-logging'
                }
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForMavenNebulaPublication')

        then:
        fileExists(pomLocation)
        println( file(pomLocation).text )
        def pom = new XmlSlurper().parse( file(pomLocation) )
        def deps = pom.dependencies.dependency
        deps.find { it.artifactId.text() == 'asm' && it.groupId.text() == 'asm'}
        def httpclient = deps.find { it.artifactId.text() == 'httpclient' }
        httpclient.exclusions.exclusion.find { it.artifactId.text() == 'httpcore' && it.groupId.text() == 'org.apache.httpcomponents' }
        httpclient.exclusions.exclusion.find { it.artifactId.text() == 'commons-logging' }
    }

    def 'produces pom with dynamic versions'() {
        writeHelloWorld('nebula.hello')
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'nebula-maven-publishing'
            repositories { jcenter() }
            dependencies {
                compile 'asm:asm:2.2.+'
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForMavenNebulaPublication')

        then:
        fileExists(pomLocation)
        println( file(pomLocation).text )
        def pom = new XmlSlurper().parse( file(pomLocation) )
        def deps = pom.dependencies.dependency
        def asmDep = deps.find { it.artifactId.text() == 'asm' && it.groupId.text() == 'asm'}
        asmDep.version.text() == '2.2.3'
    }

    def 'does not resolve config too early'() {
        given: 'A plugin attempts to configure the JavaPlugin runtime config after the ResolvedMavenPlugin resolves it'
        writeHelloWorld('nebula.hello')
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'nebula-maven-publishing'
            repositories { jcenter() }
            dependencies {
                compile 'asm:asm:2.2.3'
            }
            afterEvaluate {
                project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).extendsFrom(project.configurations.create('FOOBAR'));
            }
        '''.stripIndent()

        when: 'any task is executed'
        runTasksSuccessfully('tasks')

        then: 'the task completes successfully without any configuration failure.'
        notThrown(Exception)
    }

    def 'test jar produce correct pom'() {
        writeHelloWorld('nebula.hello')
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'nebula-maven-publishing'
            apply plugin: 'nebula-test-jar'
            repositories { jcenter() }
            dependencies {
                compile 'asm:asm:3.1'
                testCompile 'junit:junit:[4.10,4.11]'
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForMavenNebulaPublication')

        then:
        fileExists(pomLocation)
        println( file(pomLocation).text )
        def pom = new XmlSlurper().parse( file(pomLocation) )
        pom.dependencies.size() == 1
        def deps = pom.dependencies.dependency
        def asmDeps = deps.findAll { it.artifactId.text() == 'asm' && it.groupId.text() == 'asm'}
        asmDeps.size() == 1
        asmDeps[0].scope.text() == 'runtime'

        def junit = deps.find { it.artifactId.text() == 'junit' && it.groupId.text() == 'junit'}
        junit.scope.text() == 'test'
        junit.version.text() == '4.11'
    }
}
