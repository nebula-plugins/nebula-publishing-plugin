package nebula.plugin.publishing.publications

import nebula.plugin.publishing.PublicationBase
import org.gradle.api.Project
import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.api.tasks.bundling.Jar

class ScaladocJarPlugin implements PublicationBase {
    @Override
    void apply(Project project) {
        project.plugins.withType(ScalaPlugin) {

            def scalaJar = addTaskLocal(project, [name       : TASK_NAME_SCALADOC_JAR,
                                                  description: TASK_DESC_SCALADOC_JAR,
                                                  group      : CORE_GROUP_BUILD,
                                                  type       : Jar])

            buildConfigureTask(project, scalaJar, ARCHIVE_CLASSIFIER_SCALADOC, CORE_TASK_SCALADOC, EXTENSION_JAR)
        }
    }
}