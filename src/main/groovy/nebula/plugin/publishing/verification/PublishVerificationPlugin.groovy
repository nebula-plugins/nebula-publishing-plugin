package nebula.plugin.publishing.verification

import com.netflix.nebula.interop.GradleKt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.attributes.Attribute
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskCollection
import org.gradle.util.GradleVersion

import java.util.concurrent.ConcurrentHashMap

class PublishVerificationPlugin implements Plugin<Project> {

    public static final Attribute<String> STATUS_SCHEME = Attribute.of('org.netflix.internal.statusScheme', String)

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
        GradleVersion minVersion = GradleVersion.version("4.8")
        GradleVersion.current() >= minVersion
    }

    private void setupPlugin(Project project, PublishVerificationExtension extension) {
        createVerificationViolationsCollector(project)
        generateStatusSchemeAttribute(project)
        project.afterEvaluate {
            SourceSet sourceSet = project.sourceSets.find { it.name == SourceSet.MAIN_SOURCE_SET_NAME }
            if (!sourceSet) return
            VerifyPublicationTask verificationTask = project.tasks.create("verifyPublication", VerifyPublicationTask)
            VerificationReportTask reportTask = getOrCreateReportTask(project, verificationTask)

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

    static class VerificationViolationsCollectorHolderExtension {
        Map<Project, ViolationsContainer> collector = new ConcurrentHashMap<>()
    }
}
