package nebula.plugin.publishing.verification

import org.gradle.api.BuildCancelledException
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Contributes a result for every project, including projects without a Java component. */
@DisableCachingByDefault(because = 'Results populate an execution-only build service on every build')
abstract class CollectPublicationVerificationTask extends DefaultTask {
    @Input
    abstract Property<String> getProjectPath()

    @Input
    abstract Property<Boolean> getVerificationRequired()

    @Internal
    abstract Property<VerificationViolationsCollectorService> getVerificationViolationsCollectorService()

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
