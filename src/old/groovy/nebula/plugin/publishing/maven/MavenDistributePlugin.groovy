package nebula.plugin.publishing.maven

import nebula.plugin.publishing.component.CustomComponentPlugin
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin

/**
 * Create a local directory to publish to, piggyback onto a distribute task
 *
 * @author mmcgarr
 */
class MavenDistributePlugin implements Plugin<Project> {

    private static Logger logger = Logging.getLogger(MavenDistributePlugin)

    protected Project project
    String distDir = 'distMaven'

    @Override
    void apply(Project project) {
        this.project = project

        // Need a source for the distribute
        project.plugins.apply(CustomComponentPlugin)

        // Need a full fledged publication
        project.plugins.apply(NebulaMavenPublishingPlugin)

        project.getPlugins().withType(PublishingPlugin.class) {
            PublishingExtension pubExt = project.getExtensions().getByType(PublishingExtension)
            pubExt.repositories.maven {
                name 'distMaven'
                url project.file(distDir).toURI().toURL()
            }
        }

        Task distTask = project.tasks.findByName('distribute') ?: project.tasks.create(name:'distribute')

        project.tasks.matching { it.name == 'publishMavenNebulaPublicationToDistMavenRepository' }.all { PublishToMavenRepository mavenDistTask ->
            distTask.dependsOn mavenDistTask

            removeFromPublish(project, mavenDistTask)
        }

        distTask.outputs.dir(distDir)

        // Clean support
        project.tasks.matching { it.name == 'clean' }.all { Task cleanTask ->
            cleanTask.dependsOn('cleanDistribute')
        }

    }

    // Remove this task from the publish task
    public static removeFromPublish(Project project, final Task task) {
        Closure closure = {
            final Task publishLifecycleTask = project.tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
            def removed = publishLifecycleTask.dependsOn.remove(task)
            if (!removed) {
                logger.info("Unable to remove ${task.name} from ${publishLifecycleTask.name}")
            }
        }
        if (project.state.executed) {
            closure.call()
        } else {
            project.afterEvaluate closure
        }
    }
}
