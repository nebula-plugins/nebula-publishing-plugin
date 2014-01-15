package nebula.plugin.publishing.ivy

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
import org.gradle.api.publish.internal.DefaultPublishingExtension
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublication
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin

/**
 * Anagolous to NebulaBaseMavenPublishingPlugin, but for Ivy
 */
class NebulaBaseIvyPublishingPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaBaseIvyPublishingPlugin);

    protected Project project

    void apply(Project project) {
        this.project = project

        project.plugins.apply(IvyPublishPlugin)

        refreshCoordinate()
        refreshDescription()
        excludes()

    }

    /**
     * Post-pone until version, status, and group are known to be good.
     */
    private void refreshCoordinate() {
        withIvyPublication { DefaultIvyPublication ivyPub ->
            // When IvyPublication is created, it captures the version, which wasn't ready at the time
            // Refreshing the version to what the user set
            ivyPub.getIdentity().revision = project.version

            // Refreshing status, since it was calculated later
            ivyPub.getDescriptor().status = project.status

            // Might as well refresh group, in-case the user has set the group after applying publication plugin
            ivyPub.getIdentity().organisation = project.group
        }
    }

    def refreshDescription() {
        withIvyPublication { IvyPublication t ->
            t.descriptor.withXml(new Action<XmlProvider>() {
                @Override
                void execute(XmlProvider x) {
                    def root = x.asNode()
                    if (project.description) {
                        use(NodeEnhancement) {
                            root / 'info' / 'description' <<  project.description
                        }
                    }
                }
            })
        }
    }

    def excludes() {
        project.plugins.withType(JavaPlugin) { // Wait for runtime conf
            withIvyPublication { DefaultIvyPublication t ->
                Configuration runtimeConfiguration = project.configurations.getByName('runtime')

                // TODO This assumes that no resolution rules were in place.
                Map<String, ModuleDependency> dependenciesMap = runtimeConfiguration.allDependencies.findAll { it instanceof ModuleDependency }.collectEntries { ModuleDependency dep ->
                    ["${dep.group}:${dep.name}".toString(), dep]
                }
                t.descriptor.withXml { XmlProvider xmlProvider ->
                    Node root = xmlProvider.asNode()
                    root?.dependencies?.dependency.each { Node dep ->
                        def org = dep.@org
                        def name = dep.@name

                        def coord = "$org:$name".toString()
                        ModuleDependency moduleDependency = dependenciesMap.get(coord)
                        if (moduleDependency && !moduleDependency.excludeRules.isEmpty()) {
                            moduleDependency.excludeRules.each { ExcludeRule rule ->
                                def exclude = dep.appendNode('exclude')
                                if(rule.group) {
                                    exclude.@org = rule.group
                                }
                                if(rule.module) {
                                    exclude.@module = rule.module
                                }
                            }
                        }

                    }
                }
            }
        }
    }


    /**
     * All Ivy Publications
     */
    def withIvyPublication(Closure withPubClosure) {
        // New publish plugin way to specify artifacts in resulting publication
        def addArtifactClosure = {

            // Wait for our plugin to be applied.
            project.plugins.withType(PublishingPlugin) { PublishingPlugin publishingPlugin ->
                DefaultPublishingExtension publishingExtension = project.getExtensions().getByType(DefaultPublishingExtension)
                publishingExtension.publications.withType(IvyPublication, withPubClosure)
            }
        }

        // It's possible that we're running in someone else's afterEvaluate, which means we need to run this immediately
        if (project.getState().executed) {
            addArtifactClosure.call()
        } else {
            project.afterEvaluate addArtifactClosure
        }
    }
}