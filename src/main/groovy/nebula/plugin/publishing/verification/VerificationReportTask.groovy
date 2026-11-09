package nebula.plugin.publishing.verification

import org.gradle.api.BuildCancelledException
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
abstract class VerificationReportTask extends DefaultTask {

    protected VerificationReportGenerator verificationReportGenerator = new VerificationReportGenerator()
    private final ListProperty<String> verificationProjectPaths

    VerificationReportTask() {
        verificationProjectPaths = project.objects.listProperty(String).convention(Collections.emptyList())
    }

    /** Empty for legacy aggregation; automatically discovered projects for isolated aggregation. */
    @Input
    ListProperty<String> getVerificationProjectPaths() {
        verificationProjectPaths
    }

    @Input
    abstract Property<String> getTargetStatus()

    @Internal
    abstract Property<VerificationViolationsCollectorService> getVerificationViolationsCollectorService()

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
