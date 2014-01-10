package nebula.plugin.publishing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar

class NebulaTestJarPlugin implements Plugin<Project>{
    private static Logger logger = Logging.getLogger(NebulaTestJarPlugin);

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        project.plugins.withType(JavaPlugin) {
            def testJar = project.tasks.create([name: 'testJar', type: Jar]) {
                classifier = 'tests'
                extension = 'jar'
                from project.sourceSets.test.output
                group 'build'
            }

            def testConf = project.configurations.create('test')
            testConf.extendsFrom(project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME))

            project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION).extendsFrom(testConf)

            project.artifacts.add('test', testJar)

            project.plugins.withType(NebulaBaseMavenPublishingPlugin) {
                it.withMavenPublication { mavenPub ->
                    mavenPub.artifact(testJar)
                }
            }
        }
    }
}
