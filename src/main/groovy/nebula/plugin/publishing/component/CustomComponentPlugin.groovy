package nebula.plugin.publishing.component

import com.google.common.base.Preconditions
import nebula.core.AlternativeArchiveTask
import nebula.plugin.publishing.ConfsVisiblePlugin
import org.gradle.api.DomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.tasks.bundling.AbstractArchiveTask

/**
 * Common publishing attributes
 */
class CustomComponentPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(CustomComponentPlugin);

    protected Project project
    protected CustomUsage javaUsage
    protected CustomUsage webUsage
    protected DomainObjectSet<CustomUsage> usages

    CustomSoftwareComponent component

    CustomComponentExtension extension

    CustomSoftwareComponent getComponent() {
        // Register usages
        return component
    }

    void apply(Project project) {
        this.project = project

        // We need compile to be visible for CustomUsage's collectPublicDependencies
        project.plugins.apply(ConfsVisiblePlugin)

        // Create the NamedDomainObjectContainers
        usages = new HashSafeDomainObjectSet<CustomUsage>(CustomUsage)

        // Create and install the extension object
        // TODO Why is this better than project.extensions.create?
        extension = project.extensions.create("customcomponent", CustomComponentExtension, usages)

        // Register a customizable software component
        component = new CustomSoftwareComponent('custom', usages)
        project.getComponents().add(component)

        // React to the jar or war plugin
        project.plugins.withType(JavaPlugin) {
            if (!webUsage) {
                def archiveTask = project.tasks.getByName('jar') // Might have to set type on the archiveTask
                javaUsage = addUsageWithTask('runtime', archiveTask, 'jar', project.configurations.getByName('runtime'))
            }
        }

        project.plugins.withType(WarPlugin) {
            if (javaUsage) {
                // Clean up Java usage
                component.usages.remove(javaUsage)
                javaUsage = null
            }
            def archiveTask = project.tasks.getByName('war') // Might have to set type on the archiveTask
            webUsage = addUsageWithTask('webapp', archiveTask, 'jar', project.configurations.getByName('runtime'))
        }
    }

    CustomUsage addUsage(String confName, PublishArtifact artifact = null, Configuration configuration = null, Set<String> confsToSkip = null) {
        Preconditions.checkArgument(confName as Boolean)

        CustomUsage.DeferredDependencies deferredDependencies = configuration?new CustomUsage.DeferredDependencies(confName, configuration, confsToSkip):null

        def usage = new CustomUsage(confName, artifact, deferredDependencies)
        component.usages.add(usage)
        return usage
    }

    /**
     * Pull together the dependencies from visible configurations and publish it
     * @param configuration which
     * @param name to publish conf as
     * @return CustomUsage created from this configuration
     */
    CustomUsage addUsage(Configuration configuration, String name = null, Set<String> confsToSkip = null) {
        def confName = name?:configuration.name

        CustomUsage.DeferredDependencies deferredDependencies = new CustomUsage.DeferredDependencies(confName, configuration, confsToSkip)

        def usage = new CustomUsage(confName, null, deferredDependencies)
        component.usages.add(usage)
        return usage
    }

    def addUsageWithTask(String confName, Task task, String artifactType, Configuration configuration, Set<String> confsToSkip = null) {
        PublishArtifact publishArtifact = wrapTaskAsArtifact(task, artifactType)

        addUsage(confName, publishArtifact, configuration, confsToSkip)
    }


    /**
     * Standard AbstractArchiveTasks are great, though they typically last a type
     */
    public static PublishArtifact wrapTaskAsArtifact(Task task, String artifactType) {
        PublishArtifact publishArtifact = null
        if (task != null) {
            if (task instanceof AbstractArchiveTask) {
                publishArtifact = new ArchivePublishArtifact(task)
                if (artifactType) {
                    publishArtifact.setType(artifactType)
                }
            } else if (task instanceof AlternativeArchiveTask) {
                AlternativeArchiveTask altTask = (AlternativeArchiveTask) task
                // This will trigger the resolution, so it needs to be part of withPublication
                File file = altTask.getArchivePath()
                String archiveName = altTask.getBaseName()
                String extension = altTask.getExtension()
                String type = artifactType ?: altTask.getExtension()
                String classifier = altTask.getClassifier()
                Date date = new Date(file.lastModified())
                publishArtifact = new DefaultPublishArtifact(archiveName, extension, type, classifier, date, file, task)
            }
        }
        publishArtifact
    }

    public static addArtifact(Project project, String confName, Task jarTask, String artifactType, String dependenciesConfName, Set<String> confsToSkip = null) {
        def dependenciesConf = project.configurations.getByName(dependenciesConfName)
        addArtifact(project, confName, jarTask, artifactType, dependenciesConf, confsToSkip)
    }

    /**
     * Helper method to add an artifact to a CustomComponent and the configuration. Will wrap the task as an PublishArchive, so that we can
     * store information like artifactType (used in ivy publications).
     *
     * @param confName Configuration must already exist
     */
    public static addArtifact(Project project, String confName, Task jarTask, String artifactType = null, Configuration dependenciesConf = null, Set<String> confsToSkip = null) {

        PublishArtifact artifact = wrapTaskAsArtifact(jarTask, artifactType)
        project.artifacts.add(confName, artifact)

        project.plugins.withType(CustomComponentPlugin) { CustomComponentPlugin componentPlugin ->
            if (dependenciesConf) {
                componentPlugin.addUsage(confName, artifact, dependenciesConf, confsToSkip)
            } else {
                componentPlugin.addUsage(confName, artifact)
            }
        }
    }

}