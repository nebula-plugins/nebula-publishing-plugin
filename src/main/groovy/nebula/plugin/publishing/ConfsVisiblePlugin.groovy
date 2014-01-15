package nebula.plugin.publishing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet

/**
 * React to the JavaPlugin and make runtime and compile configurations visible.
 */
class ConfsVisiblePlugin implements Plugin<Project>{
    private static Logger logger = Logging.getLogger(ConfsVisiblePlugin);

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        project.plugins.withType(JavaBasePlugin) {
            JavaPluginConvention convention = project.getConvention().getPlugin(JavaPluginConvention)
            // Fix visibility of confs
            convention.getSourceSets().all { SourceSet sourceSet ->
                Configuration compileConfiguration = project.configurations.findByName(sourceSet.getCompileConfigurationName());
                compileConfiguration.setVisible(true);

                Configuration runtimeConfiguration = project.configurations.findByName(sourceSet.getRuntimeConfigurationName());
                runtimeConfiguration.setVisible(true);
            }
        }
    }
}
