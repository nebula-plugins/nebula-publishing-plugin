package nebula.plugin.publishing.verification

import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.api.BuildCancelledException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class VerifyPublicationTaskSpec extends Specification {

    private static String DUMMY_LIBRARY = 'foo:bar:1.0'

    @Unroll
    def 'test releasable combinations of statuses library=#libraryStatus project=#projectStatus'() {
        given:
        def task = setupProjectAndTask(libraryStatus, projectStatus)

        when:
        task.verifyDependencies()

        then:
        noExceptionThrown()

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
        def task = setupProjectAndTask(libraryStatus, projectStatus)

        when:
        task.verifyDependencies()

        then:
        Throwable e = thrown(BuildCancelledException)
        e.message == failureMessage

        where:
        libraryStatus | projectStatus | failureMessage
        'integration' | 'milestone'   | errorMessageTemplate("integration", "milestone")
        'integration' | 'release'     | errorMessageTemplate("integration", "release")
        'milestone'   | 'release'     | errorMessageTemplate("milestone", "release")
    }


    Task setupProjectAndTask(String libraryStatus, String projectStatus) {
        Project project = ProjectBuilder.builder().build()
        project.status = projectStatus

        populateAndSetRepository(project)
        createConfigurations(project)

        def task = project.task('verify', type: VerifyPublicationTask)
        task.configure {
            details = createCollectedComponentMetadataDetails(libraryStatus)
            ignore = Collections.emptySet()
            ignoreGroups = Collections.emptySet()
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
        project.configurations {
            runtimeClasspath
        }
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

    private String errorMessageTemplate(String libraryStatus, String projectStatus) {
        """
        Module 'foo:bar' resolved to version '1.0'.
        It cannot be used because it has status: '$libraryStatus' which is less then your current project status: '$projectStatus' in your status scheme: [integration, milestone, release].
        *** OPTIONS ***
        1) Use specific version with higher status or 'latest.$projectStatus'.
        2) ignore this check with "runtimeClasspath nebulaPublishVerification.ignore('foo:bar:1.0')".
        """.stripIndent()
    }
}
