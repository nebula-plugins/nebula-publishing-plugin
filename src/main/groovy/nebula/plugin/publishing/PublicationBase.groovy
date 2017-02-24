package nebula.plugin.publishing

import nebula.plugin.publishing.ivy.IvyBasePublishPlugin
import nebula.plugin.publishing.maven.MavenBasePublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

trait PublicationBase implements Plugin<Project>, StandardTextValues {

    /**
     * Checks to see if the task already exists or not on the project.  If it does not, then it creates it.
     * @param project project to add the task too
     * @param task Map configuration of the task to create
     * @return Null if it was not created, the task object if it was.
     */
    Task addTaskLocal(Project project, Map<String, ?> task) {
        if (!project.tasks.findByName(task.get('name').toString())) {
            project.tasks.create(task)
        } else {
            null
        }
    }

    void checkAddMavenPublish(Project project, Task createdTask) {
        project.plugins.withType(MavenPublishPlugin) {
            project.plugins.apply(MavenBasePublishPlugin)

            project.publishing.publications {
                nebula(MavenPublication) {
                    artifact createdTask
                }
            }
        }
    }

    void checkAddIvyPublish(Project project, Task createdTask, String classifier) {
        project.plugins.withType(IvyPublishPlugin) {
            project.plugins.apply(IvyBasePublishPlugin)

            project.publishing.publications {
                nebulaIvy(IvyPublication) {
                    artifact(createdTask) {
                        type classifier
                        conf classifier
                    }
                }
            }
        }
    }

    void buildConfigureTask(Project project, Task createdTask, String classifierString, String dependsOnTask,
                            String artifactExtension, boolean fromSources = false, sourceSetNames = []) {

        if (createdTask != null) {
            /*
             the addTaskLocal checks to see if the task already exists or not.  If it returns null,
             that means it did not create the task.  If it returned the task object, then it created it
             We dont want to configure things if we didn't create the task
            */

            // this means it was just created correctly, now reprocess it
            def dependsOnTaskObject = project.tasks.getByName(dependsOnTask)

            createdTask.configure {
                dependsOn dependsOnTaskObject
                if (fromSources) {
                    sourceSetNames.each {
                        from project.sourceSets.findByName(it).allSource
                    }
                } else {
                    from dependsOnTaskObject.destinationDir
                }

                classifier classifierString
                extension artifactExtension
            }

            checkAddMavenPublish(project, createdTask)
            checkAddIvyPublish(project, createdTask, classifierString)
        }
    }
}