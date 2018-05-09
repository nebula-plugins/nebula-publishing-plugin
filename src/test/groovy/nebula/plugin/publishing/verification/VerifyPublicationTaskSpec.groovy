package nebula.plugin.publishing.verification

import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class VerifyPublicationTaskSpec extends Specification {

    private static String DUMMY_LIBRARY = 'foo:bar:1.0'

    @Unroll
    def 'test releasable combinations of statuses library=#libraryStatus project=#projectStatus'() {
        given:
        Project project = ProjectBuilder.builder().build()
        def task = setupProjectAndTask(project, libraryStatus, projectStatus)

        when:
        task.verifyDependencies()

        then:
        noExceptionThrown()
        def holderExtension = project.extensions.findByType(PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension)
        holderExtension.collector.size() == 1
        def violations = holderExtension.collector[project]
        violations.size() == 0

        where:
        libraryStatus | projectStatus
        'integration' | 'integration'
        'milestone'   | 'integration'
        'milestone'   | 'milestone'
        'release'     | 'integration'
        'release'     | 'milestone'
        'release'     | 'release'

    }

    @Unroll
    def 'test failing combinations of statuses library=#libraryStatus project=#projectStatus'() {
        given:
        Project project = ProjectBuilder.builder().build()
        def task = setupProjectAndTask(project, libraryStatus, projectStatus)

        when:
        task.verifyDependencies()

        then:
        noExceptionThrown()
        def holderExtension = project.extensions.findByType(PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension)
        holderExtension.collector.size() == 1
        def violations = holderExtension.collector[project]
        violations.size() == 1
        def violation = violations.first()
        violation.id.group == 'foo'
        violation.id.name == 'bar'
        violation.metadata.status == libraryStatus

        where:
        libraryStatus | projectStatus
        'integration' | 'milestone'
        'integration' | 'release'
        'milestone'   | 'release'
    }


    Task setupProjectAndTask(Project project, String libraryStatus, String projectStatus) {
        project.extensions.create('collectorExtension', PublishVerificationPlugin.VerificationViolationsCollectorHolderExtension)
        project.plugins.apply(JavaPlugin)
        project.status = projectStatus

        populateAndSetRepository(project)
        createConfigurations(project)

        def task = project.task('verify', type: VerifyPublicationTask)
        task.configure {
            details = createCollectedComponentMetadataDetails(libraryStatus)
            ignore = Collections.emptySet()
            ignoreGroups = Collections.emptySet()
            sourceSet = project.sourceSets.main
        }
    }

    private void populateAndSetRepository(Project project) {
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(DUMMY_LIBRARY)
        DependencyGraph graph = builder.build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestIvyRepo()

        project.repositories {
            ivy {
                url generator.getIvyRepoUrl()
                layout('pattern') {
                    ivy '[organisation]/[module]/[revision]/[module]-[revision]-ivy.[ext]'
                    artifact '[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]'
                    m2compatible = true
                }
            }
        }
    }

    private void createConfigurations(Project project) {
        project.dependencies {
            runtimeClasspath DUMMY_LIBRARY
        }
    }

    private Map<DefaultModuleVersionIdentifier, ComponentMetadataDetails> createCollectedComponentMetadataDetails(String libraryStatus) {
        def detailsMock = Mock(ComponentMetadataDetails) {
            getStatus() >> libraryStatus
            getStatusScheme() >> ['integration', 'milestone', 'release']
        }

        def splitId = DUMMY_LIBRARY.split(":")
        [(new DefaultModuleVersionIdentifier(splitId[0], splitId[1], splitId[2])): detailsMock]
    }
}
