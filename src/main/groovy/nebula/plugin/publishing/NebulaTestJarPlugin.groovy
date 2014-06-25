package nebula.plugin.publishing

import nebula.plugin.publishing.component.CustomComponentPlugin
import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar

/**
 * Create a test jar, containing the test classes packaged into a jar. Also create a configuration called "test",
 * which other projects can depend on.
 *
 * TODO Allow configuration name to be configurable.
 */
class NebulaTestJarPlugin implements Plugin<Project>{
    private static Logger logger = Logging.getLogger(NebulaTestJarPlugin);

    public static final String FIXTURE_CONF = 'test'
    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        project.plugins.withType(JavaPlugin) { // needed for source sets
            def jarTask = project.tasks.create([name: 'testJar', type: Jar]) {
                classifier = 'tests'
                extension = 'jar'
                from project.sourceSets.test.output // Might want source files too, this is only the classes
                group 'build'
            }

            def conf = project.configurations.maybeCreate(FIXTURE_CONF)
            Configuration testRuntimeConf = project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)
            conf.extendsFrom(testRuntimeConf)

            project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION).extendsFrom(conf)

            CustomComponentPlugin.addArtifact(project, conf.name, jarTask, 'test-jar', testRuntimeConf)

        }
    }
}
