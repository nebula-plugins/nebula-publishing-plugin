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
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
class VerifyPublicationTask extends DefaultTask {

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
        Set<ResolvedDependencyResult> firstLevel = getNonProjectDependencies(runtimeClasspath)
        List<StatusVerificationViolation> violations = new StatusVerification(ignore, ignoreGroups, project.status).verify(firstLevel)

        List<Dependency> definedDependencies = getDefinedDependencies()
        List<VersionSelectorVerificationViolation> versionViolations = new VersionSelectorVerification(ignore, ignoreGroups).verify(definedDependencies)

        getViolations().put(project,
                new ViolationsContainer(statusViolations:  violations, versionSelectorViolations: versionViolations))
    }

    private Set<ResolvedDependencyResult> getNonProjectDependencies(Configuration runtimeClasspath) {
        Set<? extends DependencyResult> firstLevelDependencies = runtimeClasspath.incoming.resolutionResult.root.getDependencies()
                .findAll { !it.constraint }
        List<UnresolvedDependencyResult> unresolvedDependencies = firstLevelDependencies.findAll { it instanceof UnresolvedDependencyResult } as List<UnresolvedDependencyResult>
        if (! unresolvedDependencies.isEmpty()) {
            UnresolvedDependencyResult unresolvedDependencyResult = (UnresolvedDependencyResult) unresolvedDependencies.first()
            throw unresolvedDependencyResult.failure
        }
        firstLevelDependencies.findAll { DependencyResult result ->
            result instanceof ResolvedDependencyResult && ! (result.selected.id instanceof ProjectComponentIdentifier)
        } as Set<ResolvedDependencyResult>
    }

    private Map<Project, ViolationsContainer> getViolations() {
        PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension extension = project.rootProject.extensions
                .findByType(PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension)
        extension.collector
    }

    private List<Dependency> getDefinedDependencies() {
        project.configurations.collect { Configuration configuration ->
            configuration.dependencies
        }.flatten() as List<Dependency>
    }
}
