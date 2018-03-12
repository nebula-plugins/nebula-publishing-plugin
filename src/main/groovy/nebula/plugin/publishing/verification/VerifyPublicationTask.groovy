package nebula.plugin.publishing.verification

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction

class VerifyPublicationTask extends DefaultTask {

    @Input
    Map<ModuleVersionIdentifier, ComponentMetadataDetails> details
    @Input
    Set<ModuleIdentifier> ignore
    @Input
    Set<String> ignoreGroups
    @Input
    SourceSet sourceSet

    @TaskAction
    void verifyDependencies() {
        if (sourceSet == null) throw new IllegalStateException('sourceSet must be configured')
        Configuration runtimeClasspath = project.configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName())
        Map<String, DefinedDependency> definedDependencies = collectDefinedDependencies(runtimeClasspath, [:])
        Set<DependencyResult> firstLevel = getNonProjectDependencies(runtimeClasspath)
        new Verification(ignore, ignoreGroups, project.status).verify(firstLevel, details, definedDependencies)
    }

    private Set<ResolvedDependencyResult> getNonProjectDependencies(Configuration runtimeClasspath) {
        runtimeClasspath.incoming.resolutionResult.root.getDependencies().findAll {
            ! (it.selected.id instanceof ProjectComponentIdentifier)
        } as Set<ResolvedDependencyResult>
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
