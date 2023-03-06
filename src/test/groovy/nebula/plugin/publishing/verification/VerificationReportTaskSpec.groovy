package nebula.plugin.publishing.verification

import org.gradle.api.BuildCancelledException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class VerificationReportTaskSpec extends Specification {

    def 'build is unaffected when there is no violation'() {
        given:
        Project project = ProjectBuilder.builder().build()
        PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension extension = project.extensions.create('collectorExtension', PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension)
        VerificationReportTask task = project.tasks.create('report', VerificationReportTask)
        def generator = Mock(VerificationReportGenerator)
        task.verificationReportGenerator = generator
        task.targetStatus.set(project.status.toString())
        task.verificationViolationsCollectorHolderExtension.set(extension)

        when:
        task.reportViolatingDependencies()

        then:
        noExceptionThrown()

        interaction {
            0 * generator.generateReport(_, _)
        }
    }

    def 'build fails when there is at least one error in one project'() {
        given:
        Project project = ProjectBuilder.builder().build()
        project.status = 'release'
        def extension = project.extensions.create('collectorExtension', PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension)
        extension.collector.put(project, container)
        VerificationReportTask task = project.tasks.create('report', VerificationReportTask)
        def generator = Mock(VerificationReportGenerator)
        task.verificationReportGenerator = generator
        task.targetStatus.set(project.status.toString())
        task.verificationViolationsCollectorHolderExtension.set(extension)

        when:
        task.reportViolatingDependencies()

        then:
        thrown(BuildCancelledException)

        interaction {
            1 * generator.generateReport(_, project.status) >> { violations, status ->
                assert violations.size() == 1
                assert violations.values().first() == container
            }
        }

        where:
        container << [
                new ViolationsContainer(
                        statusViolations: [
                                new StatusVerificationViolation(id: Mock(ModuleVersionIdentifier))
                        ]),
                new ViolationsContainer(
                        versionSelectorViolations: [
                                new VersionSelectorVerificationViolation(dependency: Mock(Dependency))
                        ])
        ]
    }
}
