package nebula.plugin.publishing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Consolidate the publishing plugins
 */
class NebulaPublishingPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaPublishingPlugin);

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        project.plugins.apply(NebulaMavenPublishingPlugin)
    }
}