package nebula.plugin.publishing.verification

import org.gradle.api.BuildCancelledException
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Confirms a project's result for the isolated root report.
 *
 * <p>For a Java project with a main source set, this task depends on
 * {@link VerifyPublicationTask} and requires its result. Other projects contribute
 * an empty result without resolving dependencies. Completion is recorded separately
 * from verification so a disabled contribution cannot pass using a verifier's result.</p>
 *
 * <p>This task has no outputs and must execute again on configuration-cache reuse:
 * the collector is a fresh execution-only service for each build invocation.</p>
 *
 * @see VerificationReportTask
 */
@DisableCachingByDefault(because = 'Results populate an execution-only build service on every build')
abstract class CollectPublicationVerificationTask extends DefaultTask {
    /** Absolute project identity, such as {@code :} or {@code :platform:api}. */
    @Input
    abstract Property<String> getProjectPath()

    /** Whether a Java verification result is required; false for non-Java contributions. */
    @Input
    abstract Property<Boolean> getVerificationRequired()

    /** Invocation-scoped collector shared with verification and reporting tasks. */
    @Internal
    abstract Property<VerificationViolationsCollectorService> getVerificationViolationsCollectorService()

    /**
     * Confirms verification or contributes an empty result, then marks this contribution complete.
     *
     * @throws BuildCancelledException if required verification was skipped or disabled
     */
    @TaskAction
    void collectResult() {
        VerificationViolationsCollectorService service = verificationViolationsCollectorService.get()
        String path = projectPath.get()
        if (!verificationRequired.get()) {
            service.addProject(path, new ViolationsContainer())
        } else if (!service.collector.containsKey(path)) {
            throw new BuildCancelledException('Publication verification did not run for: ' + path +
                    '. Do not skip or disable verifyPublication.')
        }
        service.completeContribution(path)
    }
}
