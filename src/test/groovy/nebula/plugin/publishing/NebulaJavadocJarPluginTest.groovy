package nebula.plugin.publishing

import nebula.test.ProjectSpec

class NebulaJavadocJarPluginTest extends ProjectSpec {
    def 'adds artifact'() {
        when:
        project.apply plugin: 'nebula-javadoc-jar'
        project.apply plugin: 'java'

        then:
        def sourcesArtifacts = project.configurations.getByName('javadoc').artifacts
        sourcesArtifacts.size() == 1
        def artifact = sourcesArtifacts.iterator().next()
        artifact.classifier == 'javadoc'
        artifact.extension == 'jar'
        project.tasks.getByName('javadocJar')

    }
}
