package nebula.plugin.publishing.verification

import com.netflix.nebula.interop.GradleKt
import groovy.transform.CompileDynamic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

import java.util.concurrent.ConcurrentHashMap

class PublishVerificationPlugin implements Plugin<Project> {

    public static final Attribute<String> STATUS_SCHEME = Attribute.of('org.netflix.internal.statusScheme', String)

    @Override
    void apply(Project project) {
        if (shouldApplyPlugin()) {
            PublishVerificationExtension extension = project.extensions.create('nebulaPublishVerification', PublishVerificationExtension) as PublishVerificationExtension
            project.plugins.withType(JavaBasePlugin) {
                setupPlugin(project, extension)
            }
        }
    }

    private static boolean shouldApplyPlugin() {
        GradleVersion minVersion = GradleVersion.version("7.0")
        GradleVersion.current() >= minVersion
    }

    @CompileDynamic
    private void setupPlugin(Project project, PublishVerificationExtension extension) {
        createVerificationViolationsCollector(project)
        generateStatusSchemeAttribute(project)
        project.afterEvaluate {
            SourceSet sourceSet = project.sourceSets.find { it.name == SourceSet.MAIN_SOURCE_SET_NAME }
            if (!sourceSet) return
            TaskProvider<VerifyPublicationTask> verificationTask = project.tasks.register("verifyPublication", VerifyPublicationTask)
            TaskProvider<VerificationReportTask> reportTask = getOrCreateReportTask(project, verificationTask)
            VerificationViolationsCollectorHolderExtension verificationViolationsCollectorHolderExtension = project.rootProject.extensions.findByType(VerificationViolationsCollectorHolderExtension)
            verificationTask.configure(new Action<VerifyPublicationTask>() {
                @Override
                void execute(VerifyPublicationTask verifyPublicationTask) {
                    verifyPublicationTask.projectName.set(project.name)
                    verifyPublicationTask.targetStatus.set(project.status.toString())
                    verifyPublicationTask.resolvedComponentResultProvider = project.configurations.named(sourceSet.getRuntimeClasspathConfigurationName()).get().incoming.resolutionResult.rootComponent
                    verifyPublicationTask.ignore.set(extension.ignore)
                    verifyPublicationTask.ignoreGroups.set(extension.ignoreGroups)
                    verifyPublicationTask.verificationViolationsCollectorHolderExtension.set(verificationViolationsCollectorHolderExtension)
                    verifyPublicationTask.definedDependencies.set(project.configurations.collect { Configuration configuration ->
                        configuration.dependencies
                    }.flatten() as List<Dependency>)

                }
            })

            configureHooks(project, reportTask)
        }
    }

    void createVerificationViolationsCollector(Project project) {
        //root project doesn't have to fulfil condition for plugin setup so first submodule will create extension if it not created
        VerificationViolationsCollectorHolderExtension violationCollector = project.rootProject.extensions.findByType(VerificationViolationsCollectorHolderExtension)
        if (violationCollector == null) {
            project.rootProject.extensions.create('verificationViolationsCollectorHolderExtension', VerificationViolationsCollectorHolderExtension)
        }
    }

    @CompileDynamic
    private void generateStatusSchemeAttribute(Project p) {
        if(GradleKt.versionLessThan(p.gradle, "5.0")) {
            p.dependencies {
                components {
                    all { ComponentMetadataDetails details ->
                        StatusSchemaAttributeRule.modifyAttributes(details)
                    }
                }
            }
        } else {
            p.dependencies.components.all(StatusSchemaAttributeRule)
        }
    }


    private TaskProvider<VerificationReportTask> getOrCreateReportTask(Project project, TaskProvider<VerifyPublicationTask> verificationTask) {
        //root project doesn't have to fulfil condition for plugin setup so first submodule will create report task if it not created
        TaskCollection verificationReports = project.rootProject.tasks.withType(VerificationReportTask)
        TaskProvider<VerificationReportTask> verificationReportTask
        if (verificationReports.isEmpty()) {
            verificationReportTask = project.rootProject.tasks.register('verifyPublicationReport', VerificationReportTask)
        } else {
            verificationReportTask = project.rootProject.tasks.named('verifyPublicationReport', VerificationReportTask)
        }
        VerificationViolationsCollectorHolderExtension verificationViolationsCollectorHolderExtension = project.rootProject.extensions.findByType(VerificationViolationsCollectorHolderExtension)
        verificationReportTask.configure(new Action<VerificationReportTask>() {
            @Override
            void execute(VerificationReportTask reportTask) {
                reportTask.targetStatus.set(project.status.toString())
                reportTask.verificationViolationsCollectorHolderExtension.set(verificationViolationsCollectorHolderExtension)
                reportTask.dependsOn(verificationTask)
            }
        })
        return verificationReportTask
    }

    private void configureHooks(Project project, TaskProvider<VerificationReportTask> reportTask) {
        project.tasks.withType(PublishToIvyRepository).configureEach { Task task ->
            task.dependsOn(reportTask)
        }
        project.tasks.withType(PublishToMavenRepository).configureEach { Task task ->
            task.dependsOn(reportTask)
        }
    }

    static class VerificationViolationsCollectorHolderExtension {
        Map<String, ViolationsContainer> collector = new ConcurrentHashMap<>()
    }
}
