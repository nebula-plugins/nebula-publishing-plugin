package nebula.plugin.publishing.sign

import nebula.core.AlternativeArchiveTask
import nebula.plugin.publishing.component.CustomComponentPlugin
import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import org.apache.commons.lang3.text.WordUtils
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
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
 *
 * Signing can falter in two way: it can fail to create a Signatory because of lack of keys or AbstractAntTaskBackedMavenPublisher.determineMainArtifact
 * can't differentiate the primary .jar, it's signature or the pom file. AbstractAntTaskBackedMavenPublisher is only used in a few situations, but it is
 * not used via the Bintray or Artifactory plugin. Though it is used for "install" which is unavoidable. To avoid these situations, there are three way
 * which skip actual signing:
 *
 * <ul>
 *     <li>Missing signing.keyId, signing.password, signing.secretKeyRingFile
 *     <li>A property called signing.skip has a true value
 *     <li>install task is present on the command line
 * </ul>
 */
class NebulaSignPlugin implements Plugin<Project> {

    Sign signJarsTask

    void apply(Project project) {

        // Per http://www.gradle.org/docs/current/userguide/signing_plugin.html, it's well established that
        // signing.keyId, signing.password, signing.secretKeyRingFile need to be provided. While SigningExtension
        // can be configured with other values, it's best to stick with these well known names.

        // Conditionalize the actual signing on having the property settings. Otherwise leave the
        // adding 'signing' plugin
        project.plugins.apply(SigningPlugin)

        // sign all artifacts
        signJarsTask = project.tasks.create([name: 'signJars', type: Sign, group: 'Release'])

        // Conditionally sign.
        signJarsTask.dependsOn(project.configurations.getByName('archives').allArtifacts)
        project.gradle.taskGraph.whenReady {
            // Waiting for taskGraph, so that we can look for tasks to skip on

            signConfigurationOrNot(project, signJarsTask, project.gradle.taskGraph.allTasks)
        }

        // call signJar task before publish task, aka bintrayUpload
        project.task('preparePublish').dependsOn(signJarsTask)

        project.plugins.withType(NebulaBaseMavenPublishingPlugin) { NebulaBaseMavenPublishingPlugin basePlugin ->
            basePlugin.withMavenPublication { MavenPublication mavenJava ->
                // give signature files to artifact method. If the task's sign method is never called, then this callback
                // will never be called.
                signJarsTask.signatures.all { Signature signature ->
                    mavenJava.artifact(signature, new Action<MavenArtifact>() {
                        @Override
                        void execute(MavenArtifact t) {
                            t.classifier = signature.toSignArtifact.classifier != ""?signature.toSignArtifact.classifier:null
                            t.extension = signature.getType() // 'jar.asc'
                        }
                    })
                }
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

            final def pomDestination = project.file("${project.buildDir}/poms/${WordUtils.uncapitalize(pubName)}.pom")
            pomGenerator.destination = pomDestination

            AlternativeArchiveTask wrappedPom = project.tasks.create(taskName, AlternativeArchiveTask, new Action<AlternativeArchiveTask>() {

                @Override
                void execute(AlternativeArchiveTask t) {
                    t.dependsOn(pomGenerator)

                    t.setDestinationDir(pomDestination.parentFile)
                    t.setArchiveName(pomDestination.name)
                    t.setExtension('pom')
                }
            })

            // Add wrappedPom to pom configurations
            project.artifacts.add('pom', CustomComponentPlugin.wrapTaskAsArtifact(wrappedPom, 'pom'))

            project.plugins.withType(NebulaBaseMavenPublishingPlugin) { NebulaBaseMavenPublishingPlugin basePlugin ->
                basePlugin.withMavenPublication { MavenPublication mavenJava ->
                    // Instead of creating a new Usage, set the POM. Otherwise ValidatingMavenPublisher will pick
                    // up on the pom artifact and throw an exception in checkNoDuplicateArtifacts
                    ((DefaultMavenPublication) mavenJava).setPomFile( project.files(pomDestination) )

                    project.tasks.matching { it instanceof PublishToMavenRepository && it.publication == mavenJava }.all {
                        // Since the publication now requires our POM, we need to generate it first
                        it.dependsOn wrappedPom
                    }
                }
            }
        }

    }

    def signConfigurationOrNot(Project project, Sign signJarsTask, List<Task> allTasks) {
        // Provide two mechanism for avoiding signing.
        // The first is just a backdoor-hatch in-case it's just problematic for some.

        boolean hasSigningProps = project.hasProperty('signing.keyId') &&
                project.hasProperty('signing.password') &&
                project.hasProperty('signing.secretKeyRingFile')

        boolean skipProperty = (project.hasProperty('signing.skip') && Boolean.valueOf(project.property('signing-skip').toString()))
        // We know that the AbstractAntTaskBackedMavenPublisher is used for local files, and it will reject out publications
        // an InvalidMavenPublicationException. So we skip signing if we see a task that would trigger that publisher.
        boolean skipOnTask = allTasks.any { it.name.contains('ToMavenLocal') }

        boolean skipSigning = !hasSigningProps || skipProperty || skipOnTask

        if (!skipSigning) {
            // Only try to sign a configuration if we've got keys, otherwise we'll get artifacts into the archives
            // configurations with no bodies.
            signJarsTask.sign(project.configurations.getByName('archives'))
        } else {
            // Otherwise it'll complain that it has no signatories, even though it doesn't need any
            signJarsTask.required = false
        }
    }
}
