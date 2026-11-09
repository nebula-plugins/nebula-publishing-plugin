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

/**
 * Checks a Java project's runtime dependencies and declared version selectors.
 *
 * <p>Configuration supplies dependency-resolution providers, ignore policies, and target
 * status; execution does not access a project model. Results go to the execution-only
 * collector. In isolated mode, {@link CollectPublicationVerificationTask} confirms the
 * result before the root report runs.</p>
 *
 * <p>The task has no outputs and must rerun to populate a fresh collector, including
 * when the task graph is restored from the configuration cache.</p>
 *
 * @see VerificationReportTask
 */
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
    abstract Property<VerificationViolationsCollectorService> getVerificationViolationsCollectorService()


    @TaskAction
    void verifyDependencies() {
        Set<ResolvedDependencyResult> firstLevel = getNonProjectDependencies(resolvedComponentResultProvider.get())
        List<StatusVerificationViolation> violations = new StatusVerification(ignore.get(), ignoreGroups.get(), targetStatus.get()).verify(firstLevel)

        List<VersionSelectorVerificationViolation> versionViolations = new VersionSelectorVerification(ignore.get(), ignoreGroups.get()).verify(definedDependencies.get())

        verificationViolationsCollectorService.get().addProject(projectName.get(), new ViolationsContainer(statusViolations:  violations, versionSelectorViolations: versionViolations, targetStatus: targetStatus.get()))
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
