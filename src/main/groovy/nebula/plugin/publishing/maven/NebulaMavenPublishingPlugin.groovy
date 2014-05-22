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

    String component = 'java'

    @Override
    void apply(Project project) {
        this.project = project

        basePlugin = (NebulaBaseMavenPublishingPlugin) project.plugins.apply(NebulaBaseMavenPublishingPlugin)

        configurePublishingExtension()
        refreshDescription()

        project.plugins.apply(ResolvedMavenPlugin)
    }

    /**
     * Creates a publication based on the plugins that have been applied.  Currently only supports the JavaPlugin and
     * the WarPlugin.  This block essentially does the same as this Gradle DSL block:
     *        project.publishing {
     *            repositories {
     *                mavenLocal()
     *            }
     *            publications {
     *                mavenJava(MavenPublication) {
     *                    from project.components.java
     *                }
     *            }
     *        }
     */
    void configurePublishingExtension() {
        project.getExtensions().configure(PublishingExtension, new Action<PublishingExtension>() {
            @Override
            void execute(PublishingExtension pubExt) {

                if (project.plugins.findPlugin(WarPlugin) != null) {
                    component = 'web'
                    MavenPublication webPub = pubExt.publications.create("maven${component.capitalize()}", MavenPublication)
                    webPub.from(project.components.getByName(component))
                } else {
                    MavenPublication javaPub = pubExt.publications.create("maven${component.capitalize()}", MavenPublication)
                    if (project.plugins.findPlugin(JavaPlugin) != null) {
                        javaPub.from(project.components.getByName(component))
                    }
                }

                excludes()

                installTask(pubExt)
            }
        })
    }

    /**
     * Updates the publication's pom file with the project.name and project.description from Gradle.
     */
    void refreshDescription() {
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

    /**
     * Creates a task called 'install' that will mimic the 'mvn install' call from Maven.  This will also make sure that
     * there at least one publish tasks created if there aren't any repositories defined.
     *
     * @param pubExt the PublishingExtension instance to add the repository to
     */
    void installTask(PublishingExtension pubExt) {
        pubExt.repositories.mavenLocal()

        project.tasks.create(name: 'install', dependsOn: "publishMaven${component.capitalize()}PublicationToMavenLocal") << {
            // TODO Include artifacts that were published, since we commonly want to confirm that
            logger.info "Installed $project.name to ~/.m2/repository"
        }
    }

}