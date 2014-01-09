package nebula.plugin.publishing

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayPlugin
import com.jfrog.bintray.gradle.BintrayUploadTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.publish.internal.DefaultPublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin

/**
 * Instructions for publishing the nebula-plugins on bintray
 */
class NebulaBintrayPublishingPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaBintrayPublishingPlugin);

    protected Project project

    @Override
    void apply(Project project) {
        this.project = project

        def bintrayUpload = addBintray(project)

        project.plugins.withType(NebulaBaseMavenPublishingPlugin) { NebulaBaseMavenPublishingPlugin mavenPublishingPlugin ->
            mavenPublishingPlugin.withMavenPublication { MavenPublication mavenJava ->
                // Ensure everything is built before uploading
                bintrayUpload.dependsOn(mavenJava.publishableFiles)
            }
        }

        // Ensure our versions look like the project status before publishing
        def verifyStatus = project.tasks.create('verifyStatus')
        verifyStatus.doFirst {
            def hasSnapshot = project.version.contains('-SNAPSHOT')
            if (project.status == 'snapshot' && !hasSnapshot) {
                throw new GradleException("Version (${project.version}) needs -SNAPSHOT if publishing snapshot")
            }
            if (project.status == 'release' && hasSnapshot) {
                throw new GradleException("Version (${project.version}) can not have -SNAPSHOT if publishing release")
            }
        }
        bintrayUpload.dependsOn(verifyStatus)

    }

    BintrayUploadTask addBintray(Project project) {
        // Bintray Side
        project.plugins.apply(BintrayPlugin)

        BintrayExtension bintray = project.extensions.getByType(BintrayExtension)
        if (project.hasProperty('bintrayUser')) {
            bintray.user = project.property('bintrayUser')
            bintray.key = project.property('bintrayKey')
        }
        bintray.publications = ['mavenJava'] // TODO Assuming this from the other plugin
        bintray.pkg.repo = 'gradle-plugins'
        bintray.pkg.userOrg = 'nebula'
        bintray.pkg.name = project.name
        bintray.pkg.licenses = ['Apache-2.0']
        bintray.pkg.labels = ['gradle', 'nebula']
        //dryRun = project.hasProperty('dry')?project.dry:true // whether to run this as dry-run, without deploying

        BintrayUploadTask bintrayUpload = (BintrayUploadTask) project.tasks.find { it instanceof BintrayUploadTask }
        bintrayUpload.group = 'publishing'
        bintrayUpload.doLast {
//                // Bintray uploads are not marked published, that has to be manually done.
//                bintrayUpload.with {
//                    def http = bintrayUpload.createHttpClient()
//                    def repoPath = "${userOrg ?: user}/$repoName"
//                    def uploadUri = "/content/$repoPath/$packageName/$version/publish"
//                    println "Package path: $uploadUri"
//                    http.request(POST, JSON) {
//                        uri.path = uploadUri
//                        body = [discard: 'false']
//
//                        response.success = { resp ->
//                            logger.info("Published package $packageName.")
//                        }
//                        response.failure = { resp ->
//                            throw new GradleException("Could not publish package $packageName")
//                        }
//                    }
//                }
        }

        bintrayUpload
    }
}