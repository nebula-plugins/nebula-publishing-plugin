package nebula.plugin.publishing

import nebula.test.ProjectSpec

class NebulaTestJarPluginTest extends ProjectSpec {
    def 'adds artifact'() {
        when:
        project.apply plugin: 'nebula-test-jar'
        project.apply plugin: 'java'

        then:
        def sourcesArtifacts = project.configurations.getByName('test').artifacts
        sourcesArtifacts.size() == 1
        def artifact = sourcesArtifacts.iterator().next()
        artifact.classifier == 'tests'
        artifact.extension == 'jar'
        project.tasks.getByName('testJar')
    }

}
