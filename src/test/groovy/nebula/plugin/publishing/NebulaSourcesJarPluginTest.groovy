package nebula.plugin.publishing

import nebula.test.ProjectSpec

class NebulaSourcesJarPluginTest extends ProjectSpec {
    def 'adds artifact'() {
        when:
        project.apply plugin: 'nebula-source-jar'
        project.apply plugin: 'java'

        then:
        def sourcesArtifacts = project.configurations.getByName('sources').artifacts
        sourcesArtifacts.size() == 1
        def artifact = sourcesArtifacts.iterator().next()
        artifact.classifier == 'sources'
        artifact.extension == 'jar'
        project.tasks.getByName('sourceJar')

    }

}
