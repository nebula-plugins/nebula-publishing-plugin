package nebula.plugin.publishing.verification

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskCollection
import org.gradle.util.GradleVersion

import java.util.concurrent.ConcurrentHashMap

class PublishVerificationPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        if (shouldApplyPlugin()) {
            def extension = project.extensions.create('nebulaPublishVerification', PublishVerificationExtension)
            project.plugins.withType(JavaBasePlugin) {
                setupPlugin(project, extension)
            }
        }
    }

    private static boolean shouldApplyPlugin() {
        GradleVersion minVersion = GradleVersion.version("4.4")
        GradleVersion.current() >= minVersion
    }

    private void setupPlugin(Project project, PublishVerificationExtension extension) {
        createVerificationViolationsCollector(project)
        Map<ModuleVersionIdentifier, ComponentMetadataDetails> detailsCollector = componentMetadataCollector(project)
        project.afterEvaluate {
            SourceSet sourceSet = project.sourceSets.find { it.name == SourceSet.MAIN_SOURCE_SET_NAME }
            if (!sourceSet) return
            VerifyPublicationTask verificationTask = project.tasks.create("verifyPublication", VerifyPublicationTask)
            VerificationReportTask reportTask = getOrCreateReportTask(project, verificationTask)

            verificationTask.details = detailsCollector
            verificationTask.ignore = extension.ignore
            verificationTask.ignoreGroups = extension.ignoreGroups
            verificationTask.sourceSet = sourceSet
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

    private Map<ModuleVersionIdentifier, ComponentMetadataDetails> componentMetadataCollector(Project p) {
        Map<ModuleVersionIdentifier, ComponentMetadataDetails> detailsCollector = createCollector(p)
        p.dependencies {
            components {
                all { ComponentMetadataDetails details ->
                    detailsCollector.put(details.id, details)
                }
            }
        }
        detailsCollector
    }

    private VerificationReportTask getOrCreateReportTask(Project project, VerifyPublicationTask verificationTask) {
        //root project doesn't have to fulfil condition for plugin setup so first submodule will create report task if it not created
        TaskCollection verificationReports = project.rootProject.tasks.withType(VerificationReportTask)
        VerificationReportTask verificationReportTask
        if (verificationReports.isEmpty()) {
            verificationReportTask = project.rootProject.tasks.create('verifyPublicationReport', VerificationReportTask)
        } else {
            verificationReportTask = verificationReports.first()
        }
        verificationReportTask.dependsOn(verificationTask)
        return verificationReportTask
    }

    private Map<ModuleVersionIdentifier, ComponentMetadataDetails> createCollector(Project project) {
        //we need one collector per the whole build. Due caching in gradle metadata rules are invoked only once
        //which can cause that we will miss some metadata
        //root project doesn't have to fulfil condition for plugin setup so first submodule will create extension if it not created
        MetadataCollectorHolderExtension rootExtension = project.rootProject.extensions.findByType(MetadataCollectorHolderExtension)
        if (rootExtension == null) {
            return project.rootProject.extensions.create('metadataCollectorHolderExtension', MetadataCollectorHolderExtension).collector
        } else {
            return rootExtension.collector
        }
    }

    private void configureHooks(Project project, VerificationReportTask reportTask) {
        project.tasks.withType(PublishToIvyRepository) { task ->
            task.dependsOn(reportTask)
        }
        project.tasks.withType(PublishToMavenRepository) { task ->
            task.dependsOn(reportTask)
        }
        project.plugins.withId('com.jfrog.artifactory') {
            def artifactoryPublishTask = project.tasks.findByName('artifactoryPublish')
            if (artifactoryPublishTask) {
                artifactoryPublishTask.dependsOn(reportTask)
            }
            //newer version of artifactory plugin introduced this task to do actual publishing, so we have to
            //hook even for this one.
            def artifactoryDeployTask = project.tasks.findByName("artifactoryDeploy")
            if (artifactoryDeployTask) {
                artifactoryDeployTask.dependsOn(reportTask)
            }
        }
    }

    static class MetadataCollectorHolderExtension {
        Map<ModuleVersionIdentifier, ComponentMetadataDetails> collector = new ConcurrentHashMap<>()
    }

    static class VerificationViolationsCollectorHolderExtension {
        Map<Project, ViolationsContainer> collector = new ConcurrentHashMap<>()
    }
}
