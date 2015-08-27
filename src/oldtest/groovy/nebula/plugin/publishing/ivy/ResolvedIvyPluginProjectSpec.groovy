package nebula.plugin.publishing.ivy

import nebula.test.PluginProjectSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor
import spock.lang.Shared

class ResolvedIvyPluginProjectSpec extends PluginProjectSpec {

    @Override
    String getPluginName() {
        return 'resolved-ivy'
    }

    @Shared
    File mavenRepo

    @Shared
    File ivyRepo

    def setupSpec() {
        def graph = new DependencyGraph(['g:a1:1.0.0','g:a1:2.0.0'])
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestMavenRepo()
        mavenRepo = new File('build/testrepogen/mavenrepo')

        generator.generateTestIvyRepo()
        ivyRepo = new File('build/testrepogen/ivyrepo')
    }

    def 'resolves dynamic dependencies'() {
        when:
        project.group = 'test'
        project.description = 'Description'
        project.repositories.maven {
            url mavenRepo.toURL()
        }
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.plugins.apply(IvyResolvedDependenciesPlugin)
        project.apply plugin: 'java'
        project.dependencies {
            compile 'g:a1:1.+'
        }
        project.evaluate()
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        def ivy = generateTask.destination.text
        ivy.contains('org="g" name="a1"')
        ivy.contains('rev="1.0.0"')
        ivy.contains('revConstraint="1.+"')
        !ivy.contains('rev="1.+"')
    }
}
