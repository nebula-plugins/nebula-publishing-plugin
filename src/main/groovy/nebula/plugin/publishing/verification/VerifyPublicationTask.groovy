package nebula.plugin.publishing.verification

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
abstract class VerifyPublicationTask extends DefaultTask {

    @Input
    abstract SetProperty<ModuleIdentifier> getIgnore()

    @Input
    abstract SetProperty<String> getIgnoreGroups()

    @Input
    abstract Property<String> getTargetStatus()

    @Input
    abstract Property<String> getProjectName()

    @Internal
    Provider<ResolvedComponentResult> resolvedComponentResultProvider

    @Input
    abstract ListProperty<DeclaredDependency> getDefinedDependencies()

    @Internal
    abstract Property<PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension> getVerificationViolationsCollectorHolderExtension()

    @TaskAction
    void verifyDependencies() {
        Set<ResolvedDependencyResult> firstLevel = getNonProjectDependencies(resolvedComponentResultProvider.get())
        List<StatusVerificationViolation> violations = new StatusVerification(ignore.get(), ignoreGroups.get(), targetStatus.get()).verify(firstLevel)

        List<VersionSelectorVerificationViolation> versionViolations = new VersionSelectorVerification(ignore.get(), ignoreGroups.get()).verify(definedDependencies.get())

        verificationViolationsCollectorHolderExtension.get().collector.put(projectName.get(), new ViolationsContainer(statusViolations:  violations, versionSelectorViolations: versionViolations))
    }

    private static Set<ResolvedDependencyResult> getNonProjectDependencies(ResolvedComponentResult resolvedComponentResult) {
        Set<? extends DependencyResult> firstLevelDependencies = resolvedComponentResult.dependencies
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
}
