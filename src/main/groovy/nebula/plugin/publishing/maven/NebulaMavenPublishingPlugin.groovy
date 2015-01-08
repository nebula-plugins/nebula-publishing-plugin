package nebula.plugin.publishing.maven

import nebula.plugin.publishing.component.CustomComponentPlugin
import nebula.plugin.publishing.component.CustomSoftwareComponent
import nebula.plugin.publishing.component.CustomUsage
import nebula.plugin.publishing.xml.NodeEnhancement
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.component.Usage
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.ProjectDependencyPublicationResolver
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenDependency
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal

/**
 * Opininated plugin that creates a default publication called mavenNebula
 * TODO Break into smaller plugins
 */
class NebulaMavenPublishingPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(NebulaMavenPublishingPlugin);

    protected Project project
    NebulaBaseMavenPublishingPlugin basePlugin

    @Override
    void apply(Project project) {
        this.project = project

        basePlugin = (NebulaBaseMavenPublishingPlugin) project.plugins.apply(NebulaBaseMavenPublishingPlugin)

        def customComponentPlugin = project.plugins.apply(CustomComponentPlugin)
        basePlugin.withMavenPublication { MavenPublication mavenPub ->
            fromSoftwareComponent(mavenPub, customComponentPlugin.component)
        }

        configurePublishingExtension()
        refreshDescription(project)

        project.plugins.apply(ResolvedMavenPlugin)
        project.plugins.apply(PomDevelopersPlugin)

        cleanupMavenArtifacts()
    }

    /**
     * Creates a publication based on the plugins that have been applied.  Currently only supports the JavaPlugin and
     * the WarPlugin.  This block essentially does the same as this Gradle DSL block:
     *        project.publishing {
     *            repositories {
     *                mavenLocal()
     *            }
     *            publications {
     *                mavenJava(MavenPublication) {
     *                    from project.components.java
     *                }
     *            }
     *        }
     */
    void configurePublishingExtension() {
        project.getExtensions().configure(PublishingExtension, new Action<PublishingExtension>() {
            @Override
            void execute(PublishingExtension pubExt) {
                pubExt.publications.create("mavenNebula", MavenPublication)
                excludes()
                installTask(pubExt)
            }
        })
    }

    /**
     * Updates the publication's pom file with the project.name and project.description from Gradle.
     */
    void refreshDescription(Project project) {
        basePlugin.withMavenPublication { MavenPublication t ->
            t.pom.withXml(new Action<XmlProvider>() {
                @Override
                void execute(XmlProvider x) {
                    def root = x.asNode()
                    if (project.description) {
                        use(NodeEnhancement) {
                            (root / 'name') << project.name
                            (root / 'description') <<  project.description
                        }
                    }
                }
            })
        }
    }

    def excludes() {
        project.plugins.withType(JavaPlugin) { // Wait for runtime conf
            basePlugin.withMavenPublication { DefaultMavenPublication mavenJava ->
                Configuration runtimeConfiguration = project.configurations.getByName('runtime')

                // TODO This assumes that no resolution rules were in place.
                Map<String, ModuleDependency> dependenciesMap = runtimeConfiguration.allDependencies.findAll { it instanceof ModuleDependency }.collectEntries { ModuleDependency dep ->
                    ["${dep.group}:${dep.name}".toString(), dep]
                }
                mavenJava.pom.withXml { XmlProvider xmlProvider ->
                    Node root = xmlProvider.asNode()
                    root?.dependencies?.dependency.each { Node dep ->
                        def org = dep.groupId.text()
                        def name = dep.artifactId.text()

                        def coord = "$org:$name".toString()
                        ModuleDependency moduleDependency = dependenciesMap.get(coord)
                        if (moduleDependency && !moduleDependency.excludeRules.isEmpty()) {
                            def exclusions = dep.appendNode('exclusions')
                            moduleDependency.excludeRules.each { ExcludeRule rule ->
                                def exclude = exclusions.appendNode('exclusion')
                                // TODO Confirm that one can exist without the other. maven-4.0.0.xsd says this is valid
                                if(rule.group) {
                                    exclude.appendNode('groupId', rule.group)
                                }
                                if(rule.module) {
                                    exclude.appendNode('artifactId', rule.module)
                                }
                            }
                        }
                    }

                    if (project.plugins.findPlugin(WarPlugin)) {
                        asNode().dependencies.dependency.findAll {
                            it.scope.text() == JavaPlugin.RUNTIME_CONFIGURATION_NAME && project.configurations.getByName('providedCompile').allDependencies.find { dep ->
                                dep.name == it.artifactId.text()
                            }
                        }.each { runtimeDep ->
                            runtimeDep.scope*.value = 'provided'
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates a task called 'install' that will mimic the 'mvn install' call from Maven.
     *
     * @param pubExt the PublishingExtension instance to add the repository to
     */
    void installTask(PublishingExtension pubExt) {

        // Could have used publishMavenNebulaPublicationToMavenLocal which was created because of the above line
        project.tasks.create(name: 'install', dependsOn: "publishToMavenLocal") << {
            // TODO Include artifacts that were published, since we commonly want to confirm that
            logger.info "Installed $project.name to ~/.m2/repository"
        }
    }

    /**
     * Ensures that no plugin will come in and add a jar artifact to the MavenPublication that will cause a war
     * publication to fail.
     */
    private void cleanupMavenArtifacts() {
        project.plugins.withType(WarPlugin) {
            basePlugin.withMavenPublication { MavenPublication mavenPublication ->
                MavenArtifact artifactToRemove = mavenPublication.artifacts.find{ it.extension == 'jar' && it.classifier == null }
                mavenPublication.artifacts.remove(artifactToRemove)
            }
        }
    }

    /**
     * Note: This logic was pulled from the DefaultMavenPublication.from method.
     * @param pub
     * @param component
     */
    public void fromSoftwareComponent(MavenPublication pub, CustomSoftwareComponent component) {

        component.usages.all{ Usage usage ->
            usage.artifacts.each{ PublishArtifact publishArtifact ->
                MavenArtifact existingArtifact = pub.artifacts.find{ it.extension == publishArtifact.extension && it.classifier == publishArtifact.classifier }
                if( !existingArtifact ) {
                    pub.artifact(publishArtifact)
                }
            }

            String archiveConf = usage.getName(); // Not unique?
            String dependencyConf = (usage instanceof CustomUsage && usage.deferredDependencies?.dependencyConfName )?usage.deferredDependencies?.dependencyConfName:archiveConf

            if (dependencyConf == 'runtime') {
                // MavenPublicationInternal only supports runtime dependencies
                usage.dependencies.each { ModuleDependency dependency ->
                    if (dependency instanceof ProjectDependency) {
                        addProjectDependency(pub, (ProjectDependency) dependency)
                    } else {
                        addModuleDependency(pub, dependency)
                    }
                }
            } else {
                // Let's just hard code some mappings that we do know about.
                def scopeToConfMapping = [test: 'test', webapp: 'runtime']
                if (scopeToConfMapping[dependencyConf]) {
                    basePlugin.withMavenPublication { MavenPublication t ->
                        t.pom.withXml(new Action<XmlProvider>() {
                            @Override
                            void execute(XmlProvider x) {
                                def root = x.asNode()

                                Set<MavenDependencyInternal> runtimeDeps = ((MavenPublicationInternal)pub).runtimeDependencies
                                def runtimeDepNames = runtimeDeps.collect { MavenDependencyInternal mavenDep -> "${mavenDep.groupId}:${mavenDep.artifactId}" }
                                use(NodeEnhancement) {
                                    def dependenciesNode = root / 'dependencies'
                                    usage.dependencies.each { ModuleDependency moduleDependency ->
                                        if (moduleDependency instanceof ProjectDependency) {
                                            ModuleVersionIdentifier identifier = new ProjectDependencyPublicationResolver().resolve(moduleDependency)
                                            if (!runtimeDepNames.contains("${identifier.group}:${identifier.name}")) {
                                                NebulaMavenPublishingPlugin.addProjectDependency(pub, (ProjectDependency) moduleDependency)
                                                def dependency = dependenciesNode.appendNode('dependency')
                                                dependency.appendNode('groupId', identifier.group)
                                                dependency.appendNode('artifactId', identifier.name)
                                                dependency.appendNode('version', identifier.version)
                                                dependency.appendNode('scope', scopeToConfMapping[dependencyConf])
                                            }
                                        } else {
                                            if (!runtimeDepNames.contains("${moduleDependency.group}:${moduleDependency.name}")) {
                                                def dependency = dependenciesNode.appendNode('dependency')
                                                dependency.appendNode('groupId', moduleDependency.group)
                                                dependency.appendNode('artifactId', moduleDependency.name)
                                                dependency.appendNode('version', moduleDependency.version)
                                                dependency.appendNode('scope', scopeToConfMapping[dependencyConf])
                                                // Artifacts missing
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
    }

    static void addProjectDependency(MavenPublication pub, ProjectDependency dependency) {
        ModuleVersionIdentifier identifier = new ProjectDependencyPublicationResolver().resolve(dependency);
        ((MavenPublicationInternal)pub).runtimeDependencies.add(new DefaultMavenDependency(identifier.group, identifier.name, identifier.version));
    }

    static void addModuleDependency(MavenPublication pub, ModuleDependency dependency) {
        ((MavenPublicationInternal)pub).runtimeDependencies.add(new DefaultMavenDependency(dependency.group, dependency.name, dependency.version, dependency.artifacts));
    }

}