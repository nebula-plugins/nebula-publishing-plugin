package nebula.plugin.publishing.verification

import org.gradle.api.BuildCancelledException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.provider.Provider
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class VerificationReportTaskSpec extends Specification {

    def 'existing concrete report subclasses need no new abstract methods'() {
        given:
        Project project = ProjectBuilder.builder().build()
        def service = project.gradle.sharedServices.registerIfAbsent('customCollector', VerificationViolationsCollectorService) {}
        def task = project.tasks.create('customReport', ConcreteVerificationReportTask)
        task.verificationViolationsCollectorService.set(service)
        task.usesService(service)

        when:
        task.reportViolatingDependencies()

        then:
        noExceptionThrown()
        task.verificationProjectPaths.get().isEmpty()
    }

    def 'build is unaffected when there is no violation'() {
        given:
        Project project = ProjectBuilder.builder().build()
        Provider<VerificationViolationsCollectorService> verificationViolationsCollectorServiceProvider = project.getGradle().getSharedServices().registerIfAbsent("verificationViolationsCollectorService", VerificationViolationsCollectorService.class, spec -> {
        })
        VerificationReportTask task = project.tasks.create('report', VerificationReportTask)
        def generator = Mock(VerificationReportGenerator)
        task.verificationReportGenerator = generator
        task.targetStatus.set(project.status.toString())
        task.verificationViolationsCollectorService.set(verificationViolationsCollectorServiceProvider)
        task.usesService(verificationViolationsCollectorServiceProvider)

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
        Provider<VerificationViolationsCollectorService> verificationViolationsCollectorServiceProvider = project.getGradle().getSharedServices().registerIfAbsent("verificationViolationsCollectorService", VerificationViolationsCollectorService.class, spec -> {
        })
        verificationViolationsCollectorServiceProvider.get().addProject(project.name, container)
        VerificationReportTask task = project.tasks.create('report', VerificationReportTask)
        def generator = Mock(VerificationReportGenerator)
        task.verificationReportGenerator = generator
        task.targetStatus.set(project.status.toString())
        task.verificationViolationsCollectorService.set(verificationViolationsCollectorServiceProvider)
        task.usesService(verificationViolationsCollectorServiceProvider)

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
                                new VersionSelectorVerificationViolation(dependency: GroovyMock(DeclaredDependency))
                        ])
        ]
    }
}

// A consumer subclass implementing only the task's original abstract properties.
class ConcreteVerificationReportTask extends VerificationReportTask {
    final Property<String> targetStatus = project.objects.property(String).convention('release')
    final Property<VerificationViolationsCollectorService> verificationViolationsCollectorService =
            project.objects.property(VerificationViolationsCollectorService)
}
