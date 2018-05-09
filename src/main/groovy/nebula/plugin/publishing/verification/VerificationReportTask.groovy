package nebula.plugin.publishing.verification

import org.gradle.api.BuildCancelledException
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class VerificationReportTask extends DefaultTask {

    def verificationReportGenerator = new VerificationReportGenerator()

    @TaskAction
    void reportViolatingDependencies() {
        if (project.rootProject == project) {
            reportErrors(getViolations())
        }
    }

    private Map<Project, List<StatusVerificationViolation>> getViolations() {
        PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension extension = project.rootProject.extensions
                .findByType(PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension)
        extension.collector
    }

    void reportErrors(Map<Project, List<StatusVerificationViolation>> violationsPerProject) {
        if (violationsPerProject.any { !it.value.isEmpty() } ) {
            throw new BuildCancelledException(generateReportMessage(violationsPerProject))
        }
    }


    private String generateReportMessage(Map<Project, List<StatusVerificationViolation>> violationsPerProject){
        verificationReportGenerator.generateReport(violationsPerProject.collectEntries { [it.key.toString(), it.value] }, project.status)
    }
}
