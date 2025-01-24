package nebula.plugin.publishing.maven

import nebula.plugin.publishing.BaseIntegrationTestKitSpec


class MavenPublishPluginSpec extends BaseIntegrationTestKitSpec {

    def 'should successful publish with stable publishing feature flag'() {
        buildFile << """     
            plugins {
                id 'com.netflix.nebula.maven-publish'
                id 'java'
            }      

            group = 'test.nebula.netflix'                       
            version = '1.0'
            status = 'integration' 
                    
            publishing {
                repositories {
                    maven {
                        name = 'distMaven'
                        url = project.file("\${project.layout.buildDirectory.getAsFile().get()}/distMaven").toURI().toURL()
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
        result.output.contains(":publishNebulaPublicationToDistMavenRepository")
        new File(projectDir, 'build/distMaven/test/nebula/netflix/test/1.0/test-1.0.jar').exists()
    }
}
