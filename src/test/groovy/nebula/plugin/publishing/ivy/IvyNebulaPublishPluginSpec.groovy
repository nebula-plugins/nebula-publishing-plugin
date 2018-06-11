package nebula.plugin.publishing.ivy

import nebula.test.IntegrationSpec
import spock.lang.Ignore

class IvyNebulaPublishPluginSpec extends IntegrationSpec {

    def 'should successful publish with stable publishing feature flag'() {
        given:
        buildFile << """           
            ${applyPlugin(IvyPublishPlugin)}          
            apply plugin: 'java'

            group = 'test.nebula.netflix'                       
            version = '1.0'
            status = 'integration' 
                    
            publishing {
                repositories {
                    ivy {
                        name 'distIvy'
                        url project.file("\${project.buildDir}/distIvy").toURI().toURL()
                    }
                }
            }
        """

        settingsFile << '''\
            rootProject.name = 'test'
            enableFeaturePreview('STABLE_PUBLISHING')
        '''

        when:
        def result = runTasksSuccessfully('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        result.standardOutput.contains(":publishNebulaIvyPublicationToDistIvyRepository")

        def root = new XmlSlurper().parse(new File(projectDir, 'build/distIvy/test.nebula.netflix/test/1.0/ivy-1.0.xml'))
        def artifact = root.publications.artifact[0]
        artifact.@name == 'test'
        artifact.@type == 'jar'
    }
}
