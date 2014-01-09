package nebula.plugin.publishing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.publish.internal.DefaultPublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin

/**
 * Instructions for publishing the nebula-plugins on bintray
 */
class NebulaBaseMavenPublishingPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaBaseMavenPublishingPlugin);

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        project.plugins.apply(MavenPublishPlugin)

    }

    /**
     * All Ivy Publications
     */
    def withMavenPublication(Closure withPubClosure) {
        // New publish plugin way to specify artifacts in resulting publication
        def addArtifactClosure = {

            // Wait for our plugin to be applied.
            project.plugins.withType(PublishingPlugin) { PublishingPlugin publishingPlugin ->
                DefaultPublishingExtension publishingExtension = project.getExtensions().getByType(DefaultPublishingExtension)
                publishingExtension.publications.withType(MavenPublication, withPubClosure)
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