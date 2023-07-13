package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec
import spock.lang.Ignore

class MavenPublishPluginSpec extends IntegrationSpec {

    def 'should successful publish with stable publishing feature flag'() {
        buildFile << """           
            ${applyPlugin(MavenPublishPlugin)}          
            apply plugin: 'java'

            group = 'test.nebula.netflix'                       
            version = '1.0'
            status = 'integration' 
                    
            publishing {
                repositories {
                    maven {
                        name 'distMaven'
                        url project.file("\${project.layout.buildDirectory.getAsFile().get()}/distMaven").toURI().toURL()
                    }
                }
            }
        """

        settingsFile << '''\
            rootProject.name = 'test'
        '''

        when:
        def result = runTasks('publishNebulaPublicationToDistMavenRepository')

        then:
        result.standardOutput.contains(":publishNebulaPublicationToDistMavenRepository")
        new File(projectDir, 'build/distMaven/test/nebula/netflix/test/1.0/test-1.0.jar').exists()
    }
}
