package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec

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
                        url project.file("\${project.buildDir}/distMaven").toURI().toURL()
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


    def 'does not create publication when spring cloud contract plugin is present'() {
        setup:
        buildFile << """   
            buildscript {
              repositories {
                maven {
                  url "https://plugins.gradle.org/m2/"
                }
              }
              dependencies {
                classpath "gradle.plugin.org.springframework.cloud:spring-cloud-contract-gradle-plugin:2.2.0.RELEASE"
              }
            }      
              
            ${applyPlugin(MavenPublishPlugin)}          
            apply plugin: 'java'
            apply plugin: "org.springframework.cloud.contract"

            group = 'test.nebula.netflix'                       
            version = '1.0'
            status = 'integration' 
                    
            publishing {
                repositories {
                    maven {
                        name 'distMaven'
                        url project.file("\${project.buildDir}/distMaven").toURI().toURL()
                    }
                }
            }
        """

        when:
        def result = runTasks('publishNebulaPublicationToMavenLocal')

        then:
        result.wasSkipped('publishNebulaPublicationToMavenLocal')
    }
}
