package nebula.plugin.publishing.ivy

import nebula.plugin.publishing.component.CustomComponentPlugin
import nebula.plugin.publishing.component.CustomSoftwareComponent
import nebula.plugin.publishing.component.CustomUsage
import nebula.plugin.publishing.maven.MavenDistributePlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.internal.component.Usage
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.ProjectDependencyPublicationResolver
import org.gradle.api.publish.ivy.IvyConfiguration
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependency
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublication
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository

/**
 * Analogous to NebulaMavenPublishingPlugin, but for Ivy. Netflix has some very specific requirements for the resulting
 * Ivy file which doesn't jive with how Gradle generates them. This plugin is around to provide a template for other's
 * who might want to publish to an Ivy file.
 */
class NebulaIvyPublishingPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaIvyPublishingPlugin);

    protected Project project
    NebulaBaseIvyPublishingPlugin basePlugin
    IvyPublication ivyPublication

    private Map<String, Set<String>> confExclusions = [:].withDefault { [] as Set }

    void apply(Project project) {
        this.project = project

        basePlugin = project.plugins.apply(NebulaBaseIvyPublishingPlugin)

        // Use a more flexible Component, which will already hook up with known Components.
        def customComponentPlugin = project.plugins.apply(CustomComponentPlugin)
        // this might have to be in generateDescriptorTask.doFirst
        basePlugin.withIvyPublication { IvyPublication ivyPub ->
            fromSoftwareComponent(ivyPub, customComponentPlugin.component)
        }

        // Creating the publication, essentially we're creating this:
        //        project.publishing {
        //            repositories {
        //                mavenLocal()
        //            }
        //            publications {
        //                nebula(IvyPublication) {
        //                    from project.components.java
        //                }
        //            }
        //        }

        project.getExtensions().configure(PublishingExtension, new Action<PublishingExtension>() {
            @Override
            void execute(PublishingExtension pubExt) {
                // The name of the publication is a legacy factor from the internal system
                ivyPublication = pubExt.publications.create('nebula', IvyPublication)

                installTask(pubExt)
            }
        })

        project.plugins.apply(IvyResolvedDependenciesPlugin)
    }

    /**
     * Copy from DefaultIvyPublication.from.
     * @param pub
     * @param component
     */
    public void fromSoftwareComponent(DefaultIvyPublication pub, CustomSoftwareComponent component) {

        component.usages.all { Usage usage ->
            String archiveConf = usage.getName(); // Not unique?
            String dependencyConf = (usage instanceof CustomUsage && usage.deferredDependencies?.dependencyConfName) ? usage.deferredDependencies?.dependencyConfName : archiveConf

            ensureConfigurations(pub, archiveConf, dependencyConf)

            // Gradle implicitly has this conf extend 'default' with doesn't fit most use cases

            for (PublishArtifact publishArtifact : usage.getArtifacts()) {
                pub.artifact(publishArtifact).setConf(archiveConf);
            }

            for (ModuleDependency dependency : usage.getDependencies()) {
                String confMapping = String.format("%s->%s", dependencyConf, dependency.getConfiguration());
                if (dependency instanceof ProjectDependency) {
                    addProjectDependency(pub, (ProjectDependency) dependency, confMapping);
                } else {
                    addModuleDependency(pub, dependency, confMapping);
                }
            }
        }
    }

    def ensureConfigurations(DefaultIvyPublication pub, String archiveConf, String dependencyConf) {
        def confExtends = [:].withDefault {[]}
        if (archiveConf != dependencyConf) {
            // TODO This might be optional
            confExtends.archiveConf.add(dependencyConf)
        }

        def confsProcessed = [] as Set
        Queue<String> confQueue = new LinkedList<String>()
        confQueue.addAll([archiveConf, dependencyConf])

        while (confQueue.peek()) {
            String confToAdd = confQueue.remove()
            if (!confsProcessed.contains(confToAdd)) {
                IvyConfiguration ivyConf = pub.configurations.maybeCreate(confToAdd)
                if (confToAdd == archiveConf && archiveConf != dependencyConf) {
                    ivyConf.extend(dependencyConf)
                }

                Configuration gradleConf = project.configurations.findByName(confToAdd)
                if (gradleConf) {
                    gradleConf.extendsFrom.each {
                        if (!confExclusions[confToAdd].contains(it.name)) {
                            ivyConf.extends.add(it.name)
                            confQueue << it.name
                        }
                    }
                }
                confsProcessed << confToAdd
            }
        }
    }

    void addConfExclusion(String conf, String exclusion) {
        confExclusions[conf] << exclusion
    }

    private void addProjectDependency(DefaultIvyPublication pub, ProjectDependency dependency, String confMapping) {
        ModuleVersionIdentifier identifier = new ProjectDependencyPublicationResolver().resolve(dependency);
        pub.dependencies.add(new DefaultIvyDependency(identifier.getGroup(), identifier.getName(), identifier.getVersion(), confMapping));
    }

    private void addModuleDependency(DefaultIvyPublication pub, ModuleDependency dependency, String confMapping) {
        pub.dependencies.add(new DefaultIvyDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion(), confMapping, dependency.getArtifacts()));
    }

    /**
     * Make sure we have somewhere local to publish to. The problem is that there's no good way to agree on layouts,
     * so we'll just react to the existence of a repository with "Local" in its name and create an alias task.
     */
    def installTask(PublishingExtension pubExt) {
        basePlugin.withIvyPublication {
            // TODO Ensure the repository is truely local, aka file://
            pubExt.repositories.matching { ArtifactRepository repo ->
                repo instanceof IvyArtifactRepository && repo.name.toLowerCase().contains('local')
            }.all { IvyArtifactRepository repo ->

                // TODO convert from Camel-case
                def name = "${repo.name.toLowerCase()}-publish"
                def sourceName = "publishNebulaPublicationTo${repo.name.capitalize()}Repository"

                project.tasks.matching { it.name == sourceName }.all { PublishToIvyRepository ivyLocalTask ->

                    // Make alias to publishNebulaPublicationToLocalRepository. Generated by IvyPublishDynamicTaskCreator, so just using a task name
                    project.task(name)
                            .dependsOn(sourceName)
                            .description("Publishes to a local ivy repository, ${repo.url}")

                    MavenDistributePlugin.removeFromPublish(project, ivyLocalTask)
                }

            }
        }
    }

}