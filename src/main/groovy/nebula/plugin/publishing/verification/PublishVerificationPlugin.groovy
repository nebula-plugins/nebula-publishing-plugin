package nebula.plugin.publishing.verification

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

import java.util.concurrent.ConcurrentHashMap

class PublishVerificationPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.withType(JavaPlugin) {
            setupPlugin(project)
        }
    }

    private void setupPlugin(Project project) {
        Map<ModuleVersionIdentifier, ComponentMetadataDetails> detailsCollector = componentMetadataCollector(project)

        Task verificationTask = project.tasks.create("verifyPublication", VerifyPublicationTask)
        verificationTask.configure {
            details = detailsCollector
        }
        configureHooks(project, verificationTask)
    }

    private Map<ModuleVersionIdentifier, ComponentMetadataDetails> componentMetadataCollector(Project p) {
        Map<ModuleVersionIdentifier, ComponentMetadataDetails> detailsCollector = new ConcurrentHashMap()
        p.dependencies {
            components {
                all { ComponentMetadataDetails details ->
                    detailsCollector.put(details.id, details)
                }
            }
        }
        detailsCollector
    }

    private void configureHooks(Project project, Task verificationTask) {
        project.tasks.withType(PublishToIvyRepository) { task ->
            task.dependsOn(verificationTask)
        }
        project.tasks.withType(PublishToMavenRepository) { task ->
            task.dependsOn(verificationTask)
        }
        project.plugins.withId('com.jfrog.artifactory') {
            def artifactoryPublishTask = project.tasks.findByName('artifactoryPublish')
            if (artifactoryPublishTask) {
                artifactoryPublishTask.dependsOn(verificationTask)
            }
            //newer version of artifactory plugin introduced this task to do actual publishing, so we have to
            //hook even for this one.
            def artifactoryDeployTask = project.tasks.findByName("artifactoryDeploy")
            if (artifactoryDeployTask) {
                artifactoryDeployTask.dependsOn(verificationTask)
            }
        }
    }
}
