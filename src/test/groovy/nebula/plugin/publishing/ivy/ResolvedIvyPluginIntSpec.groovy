package nebula.plugin.publishing.ivy

import nebula.test.IntegrationSpec
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor

class ResolvedIvyPluginIntSpec extends IntegrationSpec {

    def ivyLocation = 'build/publications/nebula/ivy.xml'

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
        fileExists(ivyLocation)
        println( file(ivyLocation).text )
        def pom = new XmlSlurper().parse( file(ivyLocation) )
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
        fileExists(ivyLocation)
        println( file(ivyLocation).text )
        def pom = new XmlSlurper().parse( file(ivyLocation) )
        pom.dependencies.dependency.@rev == '2.2.3'
    }

    def 'inspect output'() {
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'ivy-publish'
            apply plugin: 'resolved-ivy'

            repositories { jcenter() }

            group='test'
            version = '1.1.1'

            dependencies {
                compile(
                    [group: 'commons-collections', name: 'commons-collections', version: '3.2.+'],
                    [group: 'commons-configuration', name: 'commons-configuration', version: '1.+'],
                )
                testCompile(
                    [group: 'junit', name: 'junit', version: '4.+'],
                )
            }

            configurations {
                distribute.extendsFrom(archives)
            }

            publishing {
                publications {
                    java(IvyPublication) {
                        from components.java
                    }
                }
            }

            publishing {
                repositories {
                    ivy {
                        url "file://$buildDir/repo"
                    }
                }
            }
            '''.stripIndent()

        when:
        def result = runTasksSuccessfully('publish')

        then:
        result.wasExecuted(':publishJavaPublicationToIvyRepository')
        File ivyxml = new File(projectDir, 'build/repo/test/inspect-output/1.1.1/ivy-1.1.1.xml')
        ivyxml.exists()

        // Confirm contents
        ModuleDescriptor md = IvyHacker.readModuleDescriptor(ivyxml)
        md.status == 'integration'
        md.moduleRevisionId.revision == '1.1.1'
        md.moduleRevisionId.organisation == 'test'
        md.moduleRevisionId.branch == null
        md.moduleRevisionId.name == moduleName
        md.revision == '1.1.1'
//        md.attributes['module'] == moduleName
//        md.attributes['organisation'] == 'test'
//        md.attributes['branch'] == null
//        md.revision == '1.0.0'
        Collection<String> configurationsNames = Arrays.asList(md.configurationsNames)
        configurationsNames.contains('runtime')
        configurationsNames.contains('default')

        // publications
        md.getAllArtifacts().length == 1
        Artifact artifact = md.getAllArtifacts()[0]
        artifact.name == moduleName
        artifact.type == 'jar'
        artifact.ext == 'jar'

        // dependencies
        DependencyDescriptor dd = md.getDependencies().find { it.dependencyId.name == 'commons-collections'}
        dd.dependencyId.organisation == 'commons-collections'
        dd.dependencyRevisionId.revision == '3.2.1'
        dd.dynamicRevId.revision == '3.2.+'
    }

    def 'handle date strings'() {
        buildFile << '''
            repositories { jcenter() }

            apply plugin: 'java'
            apply plugin: 'ivy-publish'
            apply plugin: 'resolved-ivy'

            group='test'
            version = '1.1.1'

            dependencies {
                compile(
                    [group: 'com.google.template',  name: 'soy', version: '2012-12-21']
                )
                testCompile(
                    [group: 'junit', name: 'junit', version: '4.+'],
                )
            }

            configurations {
                distribute.extendsFrom(archives)
            }

            publishing {
                publications {
                    java(IvyPublication) {
                        from components.java
                    }
                }
            }

            publishing {
                repositories {
                    ivy {
                        url "file://$buildDir/repo"
                    }
                }
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('publish')

        then:
        File ivyxml = new File(projectDir, 'build/repo/test/handle-date-strings/1.1.1/ivy-1.1.1.xml')
        ivyxml.exists()
        ModuleDescriptor md = IvyHacker.readModuleDescriptor(ivyxml)

        // dependencies
        DependencyDescriptor dd = md.getDependencies().find { it.dependencyId.name == 'soy'}
        dd.dependencyId.organisation == 'com.google.template'
        dd.dependencyRevisionId.revision == '2012-12-21'
    }

    def 'handle 1.0-r706900_3 version'() {
        buildFile << '''
            repositories { jcenter() }

            apply plugin: 'java'
            apply plugin: 'ivy-publish'
            apply plugin: 'resolved-ivy'

            group='test'
            version = '1.1.1'

            dependencies {
                compile 'org.apache.servicemix.bundles:org.apache.servicemix.bundles.commons-csv:1.0-r706900_3'
            }

            configurations {
                distribute.extendsFrom(archives)
            }

            publishing {
                publications {
                    java(IvyPublication) {
                        from components.java
                    }
                }
            }

            publishing {
                repositories {
                    ivy {
                        url "file://$buildDir/repo"
                    }
                }
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully('publish')

        then:
        File ivyxml = new File(projectDir, 'build/repo/test/handle-1-0-r706900-version/1.1.1/ivy-1.1.1.xml')
        ivyxml.exists()
        ModuleDescriptor md = IvyHacker.readModuleDescriptor(ivyxml)

        // dependencies
        DependencyDescriptor dd = md.getDependencies().find { it.dependencyId.name == 'org.apache.servicemix.bundles.commons-csv'}
        dd.dependencyRevisionId.revision == '1.0-r706900_3'
    }
}
