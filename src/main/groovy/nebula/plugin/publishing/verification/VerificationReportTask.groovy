package nebula.plugin.publishing.verification

import org.gradle.api.BuildCancelledException
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
class VerificationReportTask extends DefaultTask {

    protected VerificationReportGenerator verificationReportGenerator = new VerificationReportGenerator()

    @TaskAction
    void reportViolatingDependencies() {
        if (project.rootProject == project) {
            reportErrors(getViolations())
        }
    }

    private Map<Project, ViolationsContainer> getViolations() {
        PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension extension = project.rootProject.extensions
                .findByType(PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension)
        extension.collector
    }

    void reportErrors(Map<Project, ViolationsContainer> violationsPerProject) {
        if (violationsPerProject.any { it.value.hasViolations() } ) {
            throw new BuildCancelledException(generateReportMessage(violationsPerProject))
        }
    }


    private String generateReportMessage(Map<Project, ViolationsContainer> violationsPerProject){
        verificationReportGenerator.generateReport(violationsPerProject.collectEntries { [it.key.toString(), it.value] } as Map<String, ViolationsContainer> , project.status.toString())
    }
}
