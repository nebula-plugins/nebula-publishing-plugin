package nebula.plugin.publishing.ivy

import nebula.plugin.publishing.NebulaJavadocJarPlugin
import nebula.plugin.publishing.NebulaSourceJarPlugin
import nebula.plugin.publishing.NebulaTestJarPlugin
import nebula.test.ProjectSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator
import org.apache.commons.lang3.reflect.FieldUtils
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublication
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor

class NebulaIvyPublishingPluginSpec extends ProjectSpec {
    static String repo
    def setupSpec() {
        def myGraph = [
          'test.example:foo:3.1',
          'dep:a:4.3.1',
          'dep:b:1.1.3',
          'bar:baz:4.3.1 -> dep:a:4.3.1|dep:b:1.1.3'
        ]

        def generator = new GradleDependencyGenerator(new DependencyGraph(myGraph), 'build/nebulaivypublishingpluginspec')
        generator.generateTestIvyRepo()
        repo = new File('build/nebulaivypublishingpluginspec/ivyrepo').absolutePath
    }

    def 'apply plugin'() {
        when:
        project.plugins.apply(NebulaIvyPublishingPlugin)

        then:
        project.publishing.publications.size() == 1
    }

    def 'ensure coordinates'() {
        when:
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.group = 'group2'
        project.evaluate()

        then:
        DefaultIvyPublication ivyPub = project.publishing.publications.getByName('nebula')
        DefaultIvyPublicationIdentity id = FieldUtils.readField(ivyPub, 'publicationIdentity', true)
        id.organisation == 'group2'
    }

    def 'md generated'() {
        when:
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.evaluate()
        project.tasks.all {
            println "Task: ${it.name}"
        }
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        generateTask.destination.exists()
    }

    def 'md has dependencies'() {
        when:
        project.group = 'test'
        project.description = 'Description'
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.apply plugin: 'java'

        setupIvyRepository(project)        

        project.dependencies {
            compile 'test.example:foo:3.1'
        }
        project.evaluate()
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        def ivy = generateTask.destination.text
        ivy.contains('<description>Description</description>')
        ivy.contains('organisation="test"')
        ivy.contains('org="test.example" name="foo"')
    }

    /**
     * This actually happens in NebulaBaseIvyPublishingPlugin, but I'm too lazy to test it there.
     */
    def 'md has excludes'() {
        when:
        project.group = 'test'
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.apply plugin: 'java'
        setupIvyRepository(project)
        project.dependencies {
            compile 'test.example:foo:3.1'
            compile('bar:baz:4.3.1') {
                exclude group: 'dep', module: 'a'
                exclude module: 'b'
            }
        }
        project.evaluate()
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        println generateTask.destination.text
        def ivy = new XmlSlurper().parse(generateTask.destination)
        def deps = ivy.dependencies.dependency
        deps.find { it.@org == 'test.example' && it.@name == 'foo'}
        def httpclient = deps.find { it.@name == 'baz' }
        httpclient.exclude.find { it.@module == 'a' && it.@org == 'dep' }
        httpclient.exclude.find { it.@module == 'b' }
    }

    def 'md has other usages'() {
        when:
        project.group = 'test'
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.plugins.apply(NebulaJavadocJarPlugin)
        project.plugins.apply(NebulaTestJarPlugin)
        project.plugins.apply(NebulaSourceJarPlugin)
        project.apply plugin: 'java'
        setupIvyRepository(project)
        project.dependencies {
            compile 'test.example:foo:3.1'
            runtime 'bar:baz:4.3.1'
            testCompile 'dep:a:4.3.1'
            testRuntime 'dep:b:1.1.3'
        }
        project.evaluate()
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        println generateTask.destination.text
        def ivy = new XmlSlurper().parse(generateTask.destination)
        def artifacts = ivy.publications.artifact

        artifacts.size() == 4
        def correctName = artifacts.findAll { it.@name == canonicalName }
        correctName.size() == 4
        artifacts.any {
            it.@type == 'javadoc' && it.@ext == 'jar' && it.@conf == 'javadoc' && it.@'e:classifier' == 'javadoc'
        }

    }

    def 'handle providedCompile'() {
        project.group = 'test'
        project.apply plugin: 'war'
        project.plugins.apply(NebulaIvyPublishingPlugin)
        setupIvyRepository(project)
        project.dependencies {
            providedCompile 'test.example:foo:3.1'
        }

        when:
        project.evaluate()
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        def ivy = new XmlSlurper().parse(generateTask.destination)
        def deps = ivy.dependencies.dependency
        def foo = deps.find { it?.@org == 'test.example' && it?.@name == 'foo' }
        foo?.@conf?.text() == 'provided'
    }

    def 'handle providedRuntime'() {
        project.group = 'test'
        project.apply plugin: 'war'
        project.plugins.apply(NebulaIvyPublishingPlugin)
        setupIvyRepository(project)
        project.dependencies {
            providedRuntime 'test.example:foo:3.1'
        }

        when:
        project.evaluate()
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        def ivy = new XmlSlurper().parse(generateTask.destination)
        def deps = ivy.dependencies.dependency
        def foo = deps.find { it?.@org == 'test.example' && it?.@name == 'foo' }
        foo?.@conf?.text() == 'provided'
    }

    static def setupIvyRepository(toSetup) {
        toSetup.repositories {
            ivy {
                url repo
                layout('pattern') {
                    ivy '[organisation]/[module]/[revision]/[module]-[revision]-ivy.[ext]'
                    artifact '[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]'
                    m2compatible = true
                }
            }
        }
    }
}
