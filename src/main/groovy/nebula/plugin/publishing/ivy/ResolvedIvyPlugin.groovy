package nebula.plugin.publishing.ivy

import groovy.xml.QName
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Ensures versions are resolved in resulting POM file
 */
class ResolvedIvyPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(ResolvedIvyPlugin);

    protected Project project
    NebulaBaseIvyPublishingPlugin basePlugin

    @Override
    void apply(Project project) {
        this.project = project

        // Using NebulaBaseMavenPublishingPlugin since it gives us a safe hook into all MavenPublication's
        basePlugin = (NebulaBaseIvyPublishingPlugin) project.plugins.apply(NebulaBaseIvyPublishingPlugin)

        resolved()
    }

    def resolved() {
        basePlugin.withIvyPublication {
            descriptor {
                withXml {
                    def perConfigResolutionMap = project.configurations.collectEntries({ conf ->
                        ResolutionResult resolution = conf.incoming.resolutionResult // Forces resolve of configuration
                        def resolutionMap = resolution.getAllDependencies().findAll { it instanceof ResolvedDependencyResult }.collectEntries { ResolvedDependencyResult versionResult ->
                            // DefaultModuleComponentIdentifier is a ModuleComponentIdentifier
                            return [versionResult.selected.moduleVersion.module, versionResult]
                        }
                        [conf.name, resolutionMap]
                    })

                    def md = asNode()

                    // Proper classifier namespace, admittedly, this is pretty opinionated
                    md.attributes()['xmlns:e'] = 'http://ant.apache.org/ivy/extra'
                    def classifierName = new QName('http://ant.apache.org/ivy/maven', 'classifier', 'm')
                    md.publications?.artifact.each { artifact ->
                        if (artifact.attribute(classifierName)) {
                            def classifier = artifact.attributes().remove(classifierName)
                            artifact.attributes()['e:classifier'] = classifier
                        }
                    }

                    // TODO Reduce duplicates in other configs
                    // TODO Align columns with whitespace, people seem to like them aligned.
                    md?.dependencies?.dependency.each { dep ->
                        def id = new DefaultModuleIdentifier(dep.@org, dep.@name)
                        def confs = extractConfs(dep.@conf)
                        def results = confs.collect { perConfigResolutionMap[it][id] }.findAll { it != null }

                        if (!results.isEmpty()) {
                            def version = ((ResolvedDependencyResult) results[0]).selected.moduleVersion.version
                            def oldVersion = dep.@rev
                            if (oldVersion != version) {
                                dep.@revConstraint = oldVersion
                            }
                            dep.@rev = version
                        }
                    }
                }
            }
        }
    }
    /**
     * Naive conf parsing logic
     * @param confStr
     * @return
     */
    static Set<String> extractConfs(String confStr) {
        def mappings = confStr.tokenize(';')
        def leftsides = mappings.collect { it.tokenize('->')[0] }
        return leftsides.collect { it.tokenize(',') }.flatten()
    }


}