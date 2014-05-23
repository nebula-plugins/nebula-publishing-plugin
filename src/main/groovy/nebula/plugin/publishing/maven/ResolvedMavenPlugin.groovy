package nebula.plugin.publishing.maven

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

/**
 * Ensures versions are resolved in resulting POM file
 */
class ResolvedMavenPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(ResolvedMavenPlugin);

    protected Project project
    NebulaBaseMavenPublishingPlugin basePlugin

    @Override
    void apply(Project project) {
        this.project = project

        // Using NebulaBaseMavenPublishingPlugin since it gives us a safe hook into all MavenPublication's
        basePlugin = (NebulaBaseMavenPublishingPlugin) project.plugins.apply(NebulaBaseMavenPublishingPlugin)
        project.plugins.apply(MavenPublishPlugin) // redundant given above

        resolved()
    }

    def resolved() {
        project.plugins.withType(JavaPlugin) { // Wait for runtime conf
            basePlugin.withMavenPublication { DefaultMavenPublication mavenJava ->

                mavenJava.pom.withXml { XmlProvider xmlProvider ->
                    Configuration runtimeConfiguration = project.configurations.getByName('runtime')
                    ResolutionResult resolution = runtimeConfiguration.incoming.resolutionResult // Forces resolve of configuration
                    def resolutionMap = resolution.getAllModuleVersions().collectEntries { ResolvedModuleVersionResult versionResult ->
                        [versionResult.id.module, versionResult]
                    }
                    Node root = xmlProvider.asNode()
                    root?.dependencies?.dependency.each { Node dep ->
                        def org = dep.groupId.text()
                        def name = dep.artifactId.text()

                        def id = new DefaultModuleIdentifier(org, name)
                        ResolvedModuleVersionResult versionResult = resolutionMap.get(id)
                        if(versionResult != null) {
                            dep.version[0].value = versionResult.id.version
                        }
                    }
                }
            }
        }
    }
}