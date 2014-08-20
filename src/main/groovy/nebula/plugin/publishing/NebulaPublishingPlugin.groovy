package nebula.plugin.publishing

import nebula.plugin.publishing.ivy.NebulaBaseIvyPublishingPlugin
import nebula.plugin.publishing.manifest.NebulaPublishManifestPlugin
import nebula.plugin.publishing.maven.NebulaMavenPublishingPlugin
import nebula.plugin.publishing.maven.PomDevelopersPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Consolidate the publishing plugins. Add nebula-maven-publishing and NebulaBaseIvyPublishingPlugin plugins. User will
 * need to provide their own Ivy publication (which could just be NebulaIvyPublishingPlugin). Also sets compile and
 * runtime confs as visible.
 */
class NebulaPublishingPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaPublishingPlugin);

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        // Pretty safe to include a Maven publication
        project.plugins.apply(NebulaMavenPublishingPlugin)

        project.plugins.apply(PomDevelopersPlugin)
        project.plugins.apply(NebulaPublishManifestPlugin)

        // Not doing Ivy, it's just odd to make any assumptions for people. But we can add the BaseIvy plugin, plenty safe
        project.plugins.apply(NebulaBaseIvyPublishingPlugin)

        project.plugins.apply(ConfsVisiblePlugin)
    }
}