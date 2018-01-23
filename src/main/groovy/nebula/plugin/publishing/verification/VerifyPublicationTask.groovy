package nebula.plugin.publishing.verification

import org.gradle.api.BuildCancelledException
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class VerifyPublicationTask extends DefaultTask {

    @Input
    Map<ModuleVersionIdentifier, ComponentMetadataDetails> details
    @Input
    Set<ModuleIdentifier> ignore

    @TaskAction
    void verifyDependencies() {
        Configuration runtimeClasspath = project.configurations.runtimeClasspath
        Set<ResolvedDependency> firstLevel = runtimeClasspath.resolvedConfiguration.firstLevelModuleDependencies
        Set<ResolvedDependency> forVerification = firstLevel.findAll { ! ignore.contains(it.module.id.module) }
        forVerification.each {
            ModuleVersionIdentifier id = it.module.id
            ComponentMetadataDetails metadata = details[id]
            int projectStatus = metadata.statusScheme.indexOf(project.status)
            int moduleStatus = metadata.statusScheme.indexOf(metadata.status)
            if (moduleStatus < projectStatus) {
                throw new BuildCancelledException("Module '${id}' cannot be used because it has" +
                        " status: '${metadata.status}' which is less then your current project" +
                        " status: '${project.status}' in your status scheme: ${metadata.statusScheme}")
            }
        }
    }
}
