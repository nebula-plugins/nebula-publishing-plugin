package nebula.plugin.publishing.publications

import nebula.plugin.publishing.PublicationBase
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.tasks.bundling.Jar

class GroovydocJarPlugin implements PublicationBase {
    @Override
    void apply(Project project) {
        project.plugins.withType(GroovyPlugin) {

            def groovyJar = addTaskLocal(project, [name       : TASK_NAME_GROOVYDOC_JAR,
                                                   description: TASK_DESC_GROOVYDOC_JAR,
                                                   group      : CORE_GROUP_BUILD,
                                                   type       : Jar])

            buildConfigureTask(project, groovyJar, ARCHIVE_CLASSIFIER_GROOVYDOC, CORE_TASK_GROOVYDOC, EXTENSION_JAR)
        }
    }
}