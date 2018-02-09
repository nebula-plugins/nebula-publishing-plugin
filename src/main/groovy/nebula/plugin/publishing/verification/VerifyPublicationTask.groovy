package nebula.plugin.publishing.verification

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class VerifyPublicationTask extends DefaultTask {

    @Input
    Map<ModuleVersionIdentifier, ComponentMetadataDetails> details
    @Input
    Set<ModuleIdentifier> ignore
    @Input
    Set<String> ignoreGroups

    @TaskAction
    void verifyDependencies() {
        Configuration runtimeClasspath = project.configurations.runtimeClasspath
        Map<String, DefinedDependency> definedDependencies = collectDefinedDependencies(runtimeClasspath, [:])
        Set<ResolvedDependency> firstLevel = runtimeClasspath.resolvedConfiguration.firstLevelModuleDependencies
        new Verification(ignore, ignoreGroups, project.status).verify(firstLevel, details, definedDependencies)
    }

    Map<String, DefinedDependency> collectDefinedDependencies(Configuration parentConfiguration, Map<String, DefinedDependency> collector) {
        parentConfiguration.extendsFrom.each {
            collectDefinedDependencies(it, collector)
        }
        parentConfiguration.getDependencies().each {
            if (it instanceof ExternalDependency) {
                ExternalDependency dependency = (ExternalDependency) it
                String preferredVersion = dependency.getVersionConstraint().preferredVersion
                collector.put("${it.group}:${it.name}".toString(), new DefinedDependency(parentConfiguration.name, preferredVersion))
            }
        }
        return collector
    }
}
