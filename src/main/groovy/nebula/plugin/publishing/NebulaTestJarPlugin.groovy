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

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        project.plugins.withType(JavaPlugin) { // needed for source sets
            def testJar = project.tasks.create([name: 'testJar', type: Jar]) {
                classifier = 'tests'
                extension = 'jar'
                from project.sourceSets.test.output // Might want source files too, this is only the classes
                group 'build'
            }

            def testConf = project.configurations.create('test')
            Configuration testRuntimeConf = project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)
            testConf.extendsFrom(testRuntimeConf)

            project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION).extendsFrom(testConf)

            CustomComponentPlugin.addArtifact(project, testConf.name, testJar, 'test-jar', testRuntimeConf)

            project.plugins.withType(NebulaBaseMavenPublishingPlugin) {
                it.withMavenPublication { mavenPub ->
                    mavenPub.artifact(testJar)
                }
            }

        }
    }
}
