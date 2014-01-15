package nebula.plugin.publishing

import nebula.plugin.publishing.component.CustomComponentPlugin
import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc

class NebulaJavadocJarPlugin implements Plugin<Project>{
    private static Logger logger = Logging.getLogger(NebulaJavadocJarPlugin);

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        project.plugins.withType(JavaPlugin) {
            // TODO Conditionalize logic on project==rootProject, to know if we should aggregate the javadoc

            Javadoc javadocTask = (Javadoc) project.tasks.getByName('javadoc')
            def javadocJar = project.tasks.create([name: 'javadocJar', type: Jar]) {
                dependsOn javadocTask
                from javadocTask.destinationDir
                classifier 'javadoc'
                extension 'jar'
                group 'build'
            }

            def javadocConf = project.configurations.create('javadoc')
            project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION).extendsFrom(javadocConf)

            CustomComponentPlugin.addArtifact(project, javadocConf.name, javadocJar, 'javadoc')

            project.plugins.withType(NebulaBaseMavenPublishingPlugin) {
                it.withMavenPublication { mavenPub ->
                    mavenPub.artifact(javadocJar)
                }
            }
        }
    }

    // TODO Create consolidated javadoc, for a root project
    Task addAggregateJavadoc() {
        project.task('aggregateJavadoc', type: Javadoc) {
            description = 'Aggregate all subproject docs into a single docs directory'
            source subprojects.collect {project -> project.sourceSets.main.allJava }
            classpath = files(subprojects.collect {project -> project.sourceSets.main.compileClasspath})
            destinationDir = project.file('doc')
        }
    }
}
