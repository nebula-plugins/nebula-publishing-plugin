package nebula.plugin.publishing.verification

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
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
        Set<DependencyResult> firstLevel = getNonProjectDependencies(runtimeClasspath)
        List<StatusVerificationViolation> violations = new StatusVerification(ignore, ignoreGroups, project.status).verify(firstLevel, details)

        List<Dependency> definedDependencies = getDefinedDependencies()
        List<VersionSelectorVerificationViolation> versionViolations = new VersionSelectorVerification(ignore, ignoreGroups).verify(definedDependencies)

        getViolations().put(project,
                new ViolationsContainer(statusViolations:  violations, versionSelectorViolations: versionViolations))
    }

    private Set<ResolvedDependencyResult> getNonProjectDependencies(Configuration runtimeClasspath) {
        def firstLevelDependencies = runtimeClasspath.incoming.resolutionResult.root.getDependencies()
        def unresolvedDependencies = firstLevelDependencies.findAll { it instanceof UnresolvedDependencyResult }
        if (! unresolvedDependencies.isEmpty()) {
            UnresolvedDependencyResult unresolvedDependencyResult = (UnresolvedDependencyResult) unresolvedDependencies.first()
            throw unresolvedDependencyResult.failure
        }
        firstLevelDependencies.findAll {
            ! (it.selected.id instanceof ProjectComponentIdentifier)
        } as Set<ResolvedDependencyResult>
    }

    private Map<Project, ViolationsContainer> getViolations() {
        PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension extension = project.rootProject.extensions
                .findByType(PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension)
        extension.collector
    }

    private List<Object> getDefinedDependencies() {
        project.configurations.collect { configuration ->
            configuration.dependencies
        }.flatten()
    }
}
