package nebula.plugin.publishing.maven

import nebula.plugin.publishing.xml.NodeEnhancement
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

/**
 * Opininated plugin that creates a default publication called mavenJava
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

        project.getExtensions().configure(PublishingExtension, new Action<PublishingExtension>() {
            @Override
            void execute(PublishingExtension pubExt) {

                pubExt.publications.create('mavenJava', MavenPublication)

                excludes()

                // Make sure we have somewhere to publish to
                installTask(pubExt)

            }
        })

        project.plugins.withType(JavaPlugin) {
            includeJavaComponent()
        }
        project.plugins.withType(WarPlugin) {
            includeWarComponent()
        }
        refreshDescription()

        project.plugins.apply(ResolvedMavenPlugin)
    }

    def includeJavaComponent() {
        basePlugin.withMavenPublication { MavenPublication t ->
            def javaComponent = project.components.getByName('java')
            t.from(javaComponent)
        }
    }

    def includeWarComponent() {
        basePlugin.withMavenPublication { MavenPublication t ->
            def webComponent = project.components.getByName('web')
            // TODO Include deps somehow
            t.from(webComponent)
        }
    }

    def refreshDescription() {
        basePlugin.withMavenPublication { MavenPublication t ->
            t.pom.withXml(new Action<XmlProvider>() {
                @Override
                void execute(XmlProvider x) {
                    def root = x.asNode()
                    if (project.description) {
                        use(NodeEnhancement) {
                            (root / 'name') << project.name
                            (root / 'description') <<  project.description
                        }
                    }
                }
            })
        }
    }

    def excludes() {
        project.plugins.withType(JavaPlugin) { // Wait for runtime conf
            basePlugin.withMavenPublication { DefaultMavenPublication mavenJava ->
                Configuration runtimeConfiguration = project.configurations.getByName('runtime')

                // TODO This assumes that no resolution rules were in place.
                Map<String, ModuleDependency> dependenciesMap = runtimeConfiguration.allDependencies.findAll { it instanceof ModuleDependency }.collectEntries { ModuleDependency dep ->
                    ["${dep.group}:${dep.name}".toString(), dep]
                }
                mavenJava.pom.withXml { XmlProvider xmlProvider ->
                    Node root = xmlProvider.asNode()
                    root?.dependencies?.dependency.each { Node dep ->
                        def org = dep.groupId.text()
                        def name = dep.artifactId.text()

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

    def installTask(PublishingExtension pubExt) {
        // Mimic, mvn install task

        pubExt.repositories.mavenLocal()

        project.tasks.create(name: 'install', dependsOn: 'publishMavenJavaPublicationToMavenLocal') << {
            // TODO Correct the name to which we really published to
            // TODO Include artifacts that were published, since we commonly want to confirm that
            logger.info "Installed $project.name to ~/.m2/repository"
        }
    }

}