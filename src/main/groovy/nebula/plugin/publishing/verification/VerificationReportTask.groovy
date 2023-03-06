package nebula.plugin.publishing.verification

import org.gradle.api.BuildCancelledException
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
abstract class VerificationReportTask extends DefaultTask {

    protected VerificationReportGenerator verificationReportGenerator = new VerificationReportGenerator()

    @Internal
    abstract Property<PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension> getVerificationViolationsCollectorHolderExtension()

    @Input
    abstract Property<String> getTargetStatus()

    @TaskAction
    void reportViolatingDependencies() {
        if (project.rootProject == project) {
            reportErrors(verificationViolationsCollectorHolderExtension.get().collector)
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
