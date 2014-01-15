package nebula.plugin.publishing.ivy

import nebula.test.IntegrationSpec

class ResolvedIvyPluginIntSpec extends IntegrationSpec {
    def pomLocation = 'build/publications/nebula/ivy.xml'
    def 'produces md after resolution'() {
        writeHelloWorld('nebula.hello')
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'nebula-ivy-publishing'
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
        runTasksSuccessfully('generateDescriptorFileForNebulaPublication')

        then:
        fileExists(pomLocation)
        println( file(pomLocation).text )
        def pom = new XmlSlurper().parse( file(pomLocation) )
        def deps = pom.dependencies.dependency
        deps.find { it.@name == 'asm' && it.@org == 'asm'}
        def httpclient = deps.find { it.@name== 'httpclient' }
        httpclient.exclude.find { it.@module == 'httpcore' && it.@org == 'org.apache.httpcomponents' }
        httpclient.exclude.find { it.@module == 'commons-logging' }
    }

    def 'produces md with dynamic versions'() {
        writeHelloWorld('nebula.hello')
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'nebula-ivy-publishing'
            repositories { jcenter() }
            dependencies {
                compile 'asm:asm:2.2.+'
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('generateDescriptorFileForNebulaPublication')

        then:
        fileExists(pomLocation)
        println( file(pomLocation).text )
        def pom = new XmlSlurper().parse( file(pomLocation) )
        pom.dependencies.dependency.@rev == '2.2.3'
    }

}
