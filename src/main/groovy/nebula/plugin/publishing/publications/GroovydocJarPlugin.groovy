package com.highmark.syzygy.core.plugins

import nebula.plugin.publishing.ivy.IvyBasePublishPlugin
import nebula.plugin.publishing.maven.MavenBasePublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Groovydoc

import static com.highmark.syzygy.lib.ProjectTaskCreater.addTaskLocal
import static com.highmark.syzygy.lib.standard_values.StandardPluginValues.*

class SyzygyGroovydocJarPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.withType(GroovyPlugin) {
            Groovydoc groovydocTask = (Groovydoc) project.tasks.getByName(CORE_TASK_GROOVYDOC)

            def groovyJar = addTaskLocal(project, [name       : TASK_NAME_GROOVYDOC_JAR,
                                                   description: TASK_DESC_GROOVYDOC_JAR,
                                                   type       : Jar])

            if (groovyJar != null) {
                // this means it was just created correctly, now reprocess it
                groovyJar.configure {
                    dependsOn groovydocTask
                    from groovydocTask.destinationDir
                    classifier ARCHIVE_CLASSIFIER_GROOVYDOC
                    extension 'jar'
                    group CORE_GROUP_BUILD
                }

                project.plugins.withType(MavenPublishPlugin) {
                    project.plugins.apply(MavenBasePublishPlugin)

                    project.publishing.publications {
                        nebula(MavenPublication) {
                            artifact project.tasks.findByName(TASK_NAME_GROOVYDOC_JAR)
                        }
                    }
                }

                project.plugins.withType(IvyPublishPlugin) {
                    project.plugins.apply(IvyBasePublishPlugin)

                    project.publishing.publications {
                        nebulaIvy(IvyPublication) {
                            artifact(project.tasks.findByName(TASK_NAME_GROOVYDOC_JAR)) {
                                type ARCHIVE_CLASSIFIER_GROOVYDOC
                                conf ARCHIVE_CLASSIFIER_GROOVYDOC
                            }
                        }
                    }
                }
            }
        }
    }
}