package nebula.plugin.publishing.ivy

import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import org.gradle.testkit.runner.TaskOutcome

class IvyNebulaPublishPluginSpec extends BaseIntegrationTestKitSpec {

    def 'should successful publish'() {
        given:
        buildFile << """     
            plugins {
               id 'com.netflix.nebula.ivy-publish'
               id 'java'
            }      

            group = 'test.nebula.netflix'                       
            version = '1.0'
            status = 'integration' 
                    
            publishing {
                repositories {
                    ivy {
                        name = 'distIvy'
                        url = project.file("\${project.layout.buildDirectory.getAsFile().get()}/distIvy").toURI().toURL()
                    }
                }
            }
        """

        settingsFile << '''\
            rootProject.name = 'test'
        '''

        when:
        def result = runTasks('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        result.task(":publishNebulaIvyPublicationToDistIvyRepository").outcome == TaskOutcome.SUCCESS

        def root = new XmlSlurper().parse(new File(projectDir, 'build/distIvy/test.nebula.netflix/test/1.0/ivy-1.0.xml'))
        def artifact = root.publications.artifact[0]
        artifact.@name == 'test'
        artifact.@type == 'jar'
    }
}
