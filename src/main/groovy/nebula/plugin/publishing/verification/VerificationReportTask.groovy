package nebula.plugin.publishing.verification

import org.gradle.api.BuildCancelledException
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Aggregates dependency violations and fails before repository publication when verification is incomplete.
 *
 * <p>In isolated mode the root report expects a completed contribution for every discovered
 * project. It reports violations in project-path order using each project's own target status.
 * An empty expected-path list retains historical aggregation for legacy and custom report tasks.</p>
 *
 * <p>The task has no outputs and reruns on configuration-cache reuse to read the current
 * invocation's execution-only collector.</p>
 *
 * @see CollectPublicationVerificationTask
 * @see VerificationViolationsCollectorService
 */
@DisableCachingByDefault
abstract class VerificationReportTask extends DefaultTask {

    protected VerificationReportGenerator verificationReportGenerator = new VerificationReportGenerator()
    private final ListProperty<String> verificationProjectPaths

    VerificationReportTask() {
        verificationProjectPaths = project.objects.listProperty(String).convention(Collections.emptyList())
    }

    /**
     * Projects required to contribute before the report may pass.
     *
     * <p>The isolated root report populates this automatically. The empty default preserves
     * legacy/custom task behavior. This getter is concrete so existing subclasses need no
     * additional abstract implementation.</p>
     *
     * @return the expected absolute project paths; empty for legacy aggregation
     */
    @Input
    ListProperty<String> getVerificationProjectPaths() {
        verificationProjectPaths
    }

    /** Legacy report status; isolated reports use the status stored in each project's result. */
    @Input
    abstract Property<String> getTargetStatus()

    /** Results and contribution completion for this build invocation. */
    @Internal
    abstract Property<VerificationViolationsCollectorService> getVerificationViolationsCollectorService()

    /**
     * Checks contribution completeness before rendering deterministic violation reports.
     *
     * @throws BuildCancelledException if a contribution is missing or dependencies violate policy
     */
    @TaskAction
    void reportViolatingDependencies() {
        VerificationViolationsCollectorService service = verificationViolationsCollectorService.get()
        Map<String, ViolationsContainer> collected = service.collector
        List<String> participants = verificationProjectPaths.get()
        if (participants.isEmpty()) {
            reportErrors(collected)
            return
        }

        List<String> missing = participants.findAll { !collected.containsKey(it) || !service.hasContribution(it) }
        if (!missing.isEmpty()) {
            throw new BuildCancelledException('Publication verification did not run for: ' + missing.join(', ') +
                    '. Project conventions must apply com.netflix.nebula.publish-verification locally to every project; ' +
                    'do not skip collectPublicationVerification.')
        }
        // Report in path order with each project's own status. Mixed statuses must
        // not make diagnostics depend on configuration or task execution order.
        StringBuilder messages = new StringBuilder()
        participants.sort(false).each { String path ->
            ViolationsContainer violations = collected.get(path)
            if (violations.hasViolations()) {
                messages.append(verificationReportGenerator.generateReport(Collections.singletonMap(path, violations), violations.targetStatus))
            }
        }
        if (messages.length() > 0) {
            throw new BuildCancelledException(messages.toString())
        }

    }

    void reportErrors(Map<String, ViolationsContainer> violationsPerProject) {
        if (violationsPerProject.any { it.value.hasViolations() } ) {
            throw new BuildCancelledException(generateReportMessage(violationsPerProject))
        }
    }


    private String generateReportMessage(Map<String, ViolationsContainer> violationsPerProject){
        verificationReportGenerator.generateReport(violationsPerProject.collectEntries { [it.key, it.value] } as Map<String, ViolationsContainer> , targetStatus.get())
    }
}
