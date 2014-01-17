package nebula.plugin.publishing.sign

import nebula.core.AlternativeArchiveTask
import nebula.plugin.publishing.component.CustomComponentPlugin
import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import org.apache.commons.lang3.text.WordUtils
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.Signature
import org.gradle.plugins.signing.SigningPlugin

/**
 * From: http://mike-neck.github.io/blog/2013/06/21/how-to-publish-artifacts-with-gradle-maven-publish-plugin-version-1-dot-6/
 *
 * Mike perfectly captured the non-intuitiveness that is needed to use the signing plugin, with the maven-publish plugin.
 * This plugin captures that logic, while adding some static typing and some laziness to the publication bits. Follow the
 * <a href="http://www.gradle.org/docs/current/userguide/signing_plugin.html"></a>
 * <p>
 *     signing.keyId=24875D73
 *     signing.password=secret
 *     signing.secretKeyRingFile=/Users/me/.gnupg/secring.gpg
 * </p>
 *
 * Instructions on how to create a key can be found at https://docs.sonatype.org/display/Repository/How+To+Generate+PGP+Signatures+With+Maven
 */
class NebulaSignPlugin implements Plugin<Project> {

    Sign signJarsTask

    void apply(Project project) {

        // Per http://www.gradle.org/docs/current/userguide/signing_plugin.html, it's well established that
        // signing.keyId, signing.password, signing.secretKeyRingFile need to be provided. While SigningExtension
        // can be configured with other values, it's best to stick with these well known names.

        // Conditionalize the actual signing on having the property settings. Otherwise leave the
        boolean hasSigningProps = project.hasProperty('signing.keyId') &&
                project.hasProperty('signing.password') &&
                project.hasProperty('signing.secretKeyRingFile')

        // adding 'signing' plugin
        project.plugins.apply(SigningPlugin)

        // sign all artifacts
        signJarsTask = project.tasks.create('signJars', Sign)

        // Conditionally sign.
        if(hasSigningProps) {
            // Only try to sign a configuration if we've got keys, otherwise we'll get artifacts into the archives
            // configurations with no bodies.
            signJarsTask.sign(project.configurations.getByName('archives'))
        } else {
            // Otherwise it'll complain that it has no signatories, even though it doesn't need any
            signJarsTask.required = false
        }

        // call signJar task before publish task, aka bintrayUpload
        project.task('preparePublish').dependsOn(signJarsTask)

        // extract signature file and give them proper name
//        def getSignatureFiles = {
//            def allFiles = signJarsTask.signatureFiles.collect { it }
//            def signedSources = allFiles.find { it.name.contains('-sources') }
//            def signedJavadoc = allFiles.find { it.name.contains('-javadoc') }
//            def signedJar = (allFiles - [signedSources, signedJavadoc])[0]
//            return [
//                    [archive: signedSources, classifier: 'sources', extension: 'jar.asc'],
//                    [archive: signedJavadoc, classifier: 'javadoc', extension: 'jar.asc'],
//                    [archive: signedJar,     classifier: null,      extension: 'jar.asc']
//            ]
//        }

        project.plugins.withType(NebulaBaseMavenPublishingPlugin) { NebulaBaseMavenPublishingPlugin basePlugin ->
            basePlugin.withMavenPublication { MavenPublication mavenJava ->
                // give signature files to artifact method
                signJarsTask.signatures.all { Signature signature ->
                    mavenJava.artifact(signature, new Action<MavenArtifact>() {
                        @Override
                        void execute(MavenArtifact t) {
                            t.classifier = signature.toSignArtifact.classifier
                            t.extension = 'jar.asc'
                        }
                    })
                }
//                getSignatureFiles().each {signature ->
//                    mavenJava.artifact(signature.archive, new Action<MavenArtifact>() {
//                        @Override
//                        void execute(MavenArtifact t) {
//                            t.classifier = signature.classifier
//                            t.extension = signature.extension
//                        }
//                    })
//                }
            }
        }

        // Support POM
        // Create configuration which pom can be added to, all artifacts will automatically get added to archives, which is already
        // being signed
        project.configurations.create('pom')

        // Immediately create a wrapper task when the generator appears
        project.tasks.matching { it instanceof GenerateMavenPom }.all { GenerateMavenPom pomGenerator ->
            // 'wrapGeneratePomFileForMavenJavaPublication'
            def taskName = "wrap${pomGenerator.name.capitalize()}"

            // Have GenerateMavenPom have a destination that we can sign, with a nice name
            def pubName = (pomGenerator.name =~ /For(.*)Publication/)[0][1]
            pomGenerator.destination = project.file("${project.buildDir}/poms/${WordUtils.uncapitalize(pubName)}.pom")
            AlternativeArchiveTask wrappedPom = project.tasks.create(taskName, AlternativeArchiveTask, new Action<AlternativeArchiveTask>() {

                @Override
                void execute(AlternativeArchiveTask t) {
                    t.dependsOn(pomGenerator)

                    def outputFile = pomGenerator.destination
                    t.setDestinationDir(outputFile.parentFile)
                    t.setArchiveName(outputFile.name)
                    t.setExtension('pom')
                }
            })
            CustomComponentPlugin.addArtifact(project, 'pom', wrappedPom, 'pom')
        }

    }
}
