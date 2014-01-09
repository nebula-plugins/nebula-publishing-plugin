package nebula.plugin.publishing

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

/**
 * TODO Break into smaller plugins
 */
class NebulaMavenPublishingPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaMavenPublishingPlugin);

    protected Project project
    NebulaBaseMavenPublishingPlugin basePlugin

    @Override
    void apply(Project project) {
        this.project = project

        basePlugin = (NebulaBaseMavenPublishingPlugin) project.plugins.apply(NebulaBaseMavenPublishingPlugin)
        project.plugins.apply(MavenPublishPlugin) // redundant given above

        // Creating the publication, essentially we're creating this:
        //        project.publishing {
        //            repositories {
        //                mavenLocal()
        //            }
        //            publications {
        //                mavenJava(MavenPublication) {
        //                    from project.components.java
        //                }
        //            }
        //        }

        PublishingExtension pubExt = project.getExtensions().getByType(PublishingExtension)
        pubExt.publications.create('mavenJava', MavenPublication)

        // Make sure we have somewhere to publish to
        pubExt.repositories.mavenLocal()

        refreshCoordinate()
        project.plugins.withType(JavaPlugin) {
            includeJavaComponent()
        }
        refreshPom()
        aliasInstallTask()
        excludesAndResolved()
    }

    /**
     * Add Maven side. Creating a publication and establishing pom values
     * @param project
     * @return
     */
    def refreshCoordinate() {
        // Post-pone until version and group are known to be good.
        basePlugin.withMavenPublication { DefaultMavenPublication mavenPub ->
            // When IvyPublication is created, it captures the version, which wasn't ready at the time
            // Refreshing the version to what the user set
            mavenPub.version = project.version

            // Might as well refresh group, in-case the user has set the group after applying publication plugin
            mavenPub.groupId = project.group
        }
    }

    def includeJavaComponent() {
        basePlugin.withMavenPublication { MavenPublication t ->
            def javaComponent = project.components.getByName('java')
            t.from(javaComponent)
        }
    }

    def refreshPom() {
//


        def repoName = project.name.startsWith('nebula') ? "${project.name}-plugin" : "gradle-${project.name}-plugin"
        def pomConfig = {
            // TODO Call scmprovider plugin for values
            url "https://github.com/nebula-plugins/${repoName}"

            scm {
                url "scm:git://github.com/nebula-plugins/${repoName}.git"
                connection "scm:git://github.com/nebula-plugins/${repoName}.git"
            }

            licenses {
                license {
                    name 'The Apache Software License, Version 2.0'
                    url 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                    distribution 'repo'
                }
            }
        }

        basePlugin.withMavenPublication { MavenPublication t ->
            t.pom.withXml(new Action<XmlProvider>() {
                @Override
                void execute(XmlProvider x) {
                    def root = x.asNode()
                    root.appendNode('description', project.description)
                    // TODO Replace node instead of appendNode
                    root.children().last() + pomConfig
                }
            })
        }
    }

    def aliasInstallTask() {
        // Mimic, mvn install task
        project.tasks.create(name: 'install', dependsOn: 'publishMavenJavaPublicationToMavenLocal') << {
            logger.info "Installed $project.name to ~/.m2/repository"
        }
    }


    // This is more of a Responsible plugin thing
    def excludesAndResolved() {
        project.plugins.withType(JavaPlugin) { // Wait for runtime conf
            basePlugin.withMavenPublication { DefaultMavenPublication mavenJava ->
                Configuration runtimeConfiguration = project.configurations.getByName('runtime')
                ResolutionResult resolution = runtimeConfiguration.incoming.resolutionResult // Forces resolve of configuration
                def resolutionMap = resolution.getAllModuleVersions().collectEntries { ResolvedModuleVersionResult versionResult ->
                    [versionResult.id.module, versionResult]
                }

                // TODO This assumes that no resolution rules were in place.
                Map<String, ModuleDependency> dependenciesMap = runtimeConfiguration.allDependencies.findAll { it instanceof ModuleDependency }.collectEntries { ModuleDependency dep ->
                    ["${dep.group}:${dep.name}".toString(), dep]
                }
                mavenJava.pom.withXml { XmlProvider xmlProvider ->
                    Node root = xmlProvider.asNode()
                    root?.dependencies?.dependency.each { Node dep ->
                        def org = dep.groupId.text()
                        def name = dep.artifactId.text()

                        def id = new DefaultModuleIdentifier(org, name)
                        ResolvedModuleVersionResult versionResult = resolutionMap.get(id)
                        if(versionResult != null) {
                            dep.version[0].value = versionResult.id.version
                        }

                        def coord = "$org:$name".toString()
                        ModuleDependency moduleDependency = dependenciesMap.get(coord)
                        if (moduleDependency && !moduleDependency.excludeRules.isEmpty()) {
                            def exclusions = dep.appendNode('exclusions')
                            moduleDependency.excludeRules.each { ExcludeRule rule ->
                                def exclude = exclusions.appendNode('exclusion')
                                // TODO Confirm that one can exist without the other. maven-4.0.0.xsd says this is valid
                                if(rule.group) {
                                    exclude.appendNode('groupId', rule.group)
                                }
                                if(rule.module) {
                                    exclude.appendNode('artifactId', rule.module)
                                }
                            }
                        }

                    }
                }
            }
        }
    }
}