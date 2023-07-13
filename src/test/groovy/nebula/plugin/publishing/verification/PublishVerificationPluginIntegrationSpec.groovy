package nebula.plugin.publishing.verification

import nebula.plugin.dependencylock.DependencyLockPlugin
import nebula.plugin.publishing.ivy.IvyNebulaPublishPlugin
import nebula.plugin.publishing.ivy.IvyPublishPlugin
import nebula.plugin.publishing.maven.MavenPublishPlugin
import nebula.plugin.resolutionrules.ResolutionRulesPlugin
import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.DependencyGraphNode
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import nebula.test.functional.ExecutionResult
import netflix.nebula.dependency.recommender.DependencyRecommendationsPlugin
import org.jfrog.gradle.plugin.artifactory.ArtifactoryPlugin
import spock.lang.IgnoreIf

class PublishVerificationPluginIntegrationSpec extends IntegrationSpec {

    def setup() {
        settingsFile.text = '''\
            rootProject.name='testhello'
        '''
        gradleVersion = null
    }

    def 'should successful pass through verification'() {
        given:
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def projectStatus = 'snapshot'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        def dependencies = "implementation '$expectedFailureDependency'"

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksSuccessfully('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        result.standardOutput.contains(":verifyPublication")
        result.standardOutput.contains(":publishNebulaIvyPublicationToDistIvyRepository")
    }

    def 'should fail when any library status is less then published project status'() {
        given:
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def expectedFailureDependency2 = 'foo:bar2:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        builder.addModule(expectedFailureDependency2)
        def dependencies = """
             implementation '$expectedFailureDependency'
             implementation '$expectedFailureDependency2'
        """

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
        assertStatusFailureMessage(result, expectedFailureDependency2, projectStatus)
    }

    def 'should successful pass through when any transitive library status is less then published project status'() {
        given:
        def expectedSuccessDependency = 'test.nebula:example:1.0.0'
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def expectedFailureDependency2 = 'foo:bar2:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(new ModuleBuilder(expectedSuccessDependency)
            .addDependency(expectedFailureDependency)
            .addDependency(expectedFailureDependency2)
            .build()
        )
        builder.addModule(expectedFailureDependency)
        builder.addModule(expectedFailureDependency2)
        def dependencies = """
             implementation '$expectedSuccessDependency'
        """

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasks('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        result.wasExecuted(":verifyPublication")
        !result.standardOutput.contains("Following dependencies have incorrect status lower then your current project status")
    }

    def 'should successful pass through when any transitive library status is less then published project status - with core locking constraints'() {
        given:
        def expectedSuccessDependency = 'test.nebula:example:1.0.0'
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def expectedFailureDependency2 = 'foo:bar2:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(new ModuleBuilder(expectedSuccessDependency)
            .addDependency(expectedFailureDependency)
            .addDependency(expectedFailureDependency2)
            .build()
        )
        builder.addModule(expectedFailureDependency)
        builder.addModule(expectedFailureDependency2)
        def dependencies = """
             implementation '$expectedSuccessDependency'
        """

        def propertiesFile = new File(projectDir, "gradle.properties")
        propertiesFile << "systemProp.nebula.features.coreLockingSupport=true"

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def lockingResult = runTasks('dependencies', '--write-locks')
        def result = runTasks('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        lockingResult.wasExecuted(":dependencies")
        lockingResult.standardOutput.contains("Persisted dependency lock state")
        result.wasExecuted(":verifyPublication")
        !result.standardOutput.contains("> Task :verifyPublicationReport FAILED")
        !result.standardOutput.contains("Following dependencies have incorrect status lower then your current project status")
        !result.standardOutput.contains("nebulaPublishVerification")
    }

    def 'should fail when any library has incorrect version pattern'() {
        given:
        def expectedFailureDependency = 'foo:bar:1.1+'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule('foo:bar:1.1')
        def dependencies = """
             implementation '$expectedFailureDependency'
        """

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertVersionFailureMessage(result, expectedFailureDependency)
    }

    def 'should fail when any library has incorrect version pattern and dependencies with lower status'() {
        given:
        def expectedFailureDependency = 'foo:bar:1.1+'
        def expectedFailureDependency2 = 'foo:bar2:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule('foo:bar:1.1')
        builder.addModule(expectedFailureDependency2)
        def dependencies = """
             implementation '$expectedFailureDependency'
             implementation '$expectedFailureDependency2'
        """

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertVersionFailureMessage(result, expectedFailureDependency)
        assertStatusFailureMessage(result, expectedFailureDependency2, projectStatus)
    }

    def 'should fail when any library status is less then published project status and verification task is directly called'() {
        given:
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        def dependencies = "implementation '$expectedFailureDependency'"

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksWithFailure('build', 'verifyPublicationReport')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
    }

    def 'should work with dependency lock'() {
        given:
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        def dependencies = "implementation '$expectedFailureDependency'"

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        runTasks('generateLock', 'saveLock')
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
    }

    def 'should work with dependency recommendation plugin'() {
        given:
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        def dependencies = "implementation 'foo:bar'"

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)
        buildFile << """           
            dependencyRecommendations {
                 map recommendations: ['foo:bar': '1.0-SNAPSHOT']
            }
        """

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
    }

    def 'should work with resolution rules plugin using replace rule'() {
        given:
        def expectedFailureDependency = 'replace-new:replace-new:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule('replace-original:replace-original:1.0-SNAPSHOT')
        builder.addModule(expectedFailureDependency)
        DependencyGraphNode somethingWithNew = new ModuleBuilder('something-with-new:something-with-new:1.5')
                .addDependency(expectedFailureDependency).build()
        builder.addModule(somethingWithNew)
        def dependencies = """
            implementation 'replace-original:replace-original:1.0-SNAPSHOT'
            implementation 'something-with-new:something-with-new:1.5'
        """

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
    }

    def 'should work with resolution rules plugin using alignment rule'() {
        given:
        def expectedFailureDependency = 'align-group:align-part1:1.1-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule('align-group:align-part1:1.0-SNAPSHOT')
        builder.addModule(expectedFailureDependency)
        builder.addModule('align-group:align-part2:1.1-SNAPSHOT')
        def dependencies = """
            implementation 'align-group:align-part1:1.0-SNAPSHOT'
            implementation 'align-group:align-part2:1.1-SNAPSHOT'
        """

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
    }

    def 'should work with resolution rules plugin using substitution rule'() {
        given:
        def expectedFailureDependency = 'substitute-new:substitute-new:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule('substitute-original:substitute-original:1.1-SNAPSHOT')
        builder.addModule(expectedFailureDependency)
        builder.addModule('substitute-new:substitute-new:1.1-SNAPSHOT')
        def dependencies = "implementation 'substitute-original:substitute-original:1.1-SNAPSHOT'"

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
    }

    def 'should work with resolution rules plugin using reject rule'() {
        given:
        def expectedFailureDependency = 'reject:reject:1.0.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        builder.addModule('reject:reject:1.1.0-SNAPSHOT')
        def dependencies = "implementation 'reject:reject:1.+'"

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
    }

    def 'should work with maven publish'() {
        given:
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        def dependencies = "implementation '$expectedFailureDependency'"

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksWithFailure('build', 'publishNebulaPublicationToDistMavenRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
    }


    def 'should work with forced dependency'() {
        given:
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        builder.addModule('foo:bar:1.0')
        def dependencies = "implementation 'foo:bar:1.0'"

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)
        buildFile << """
            configurations.all {
                resolutionStrategy {
                    force '$expectedFailureDependency'
                }
            }
        """

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
    }

    def 'should work with latest.integration'() {
        given:
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        def dependencies = "implementation 'foo:bar:latest.integration'"

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
    }

    def 'test runtime configuration should not be checked'() {
        given:
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule('foo:bar:1.0-SNAPSHOT')
        builder.addModule('foo:bar:1.0')
        def dependencies = """
            implementation 'foo:bar:1.0'
            testImplementation 'foo:bar:1.0-SNAPSHOT'
        """

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksSuccessfully('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        result.standardOutput.contains(":verifyPublication")
        result.standardOutput.contains(":publishNebulaIvyPublicationToDistIvyRepository")
    }

    def 'only first level dependencies are verified'() {
        given:
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        def module = new ModuleBuilder('foo:bar:1.0').addDependency('baz:buz:1.0-SNAPSHOT').build()
        builder.addModule(module)
        def dependencies = "implementation 'foo:bar:1.0'"

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)

        when:
        def result = runTasksSuccessfully('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        result.standardOutput.contains(":verifyPublication")
        result.standardOutput.contains(":publishNebulaIvyPublicationToDistIvyRepository")
    }

    def 'ignored dependencies are not verified'() {
        given:
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule('some.group:ignore-as-string:1.0-SNAPSHOT')
        builder.addModule('some.group:ignore-as-map:1.0-SNAPSHOT')
        builder.addModule('ignore.as.group:some-artifact:1.0-SNAPSHOT')
        builder.addModule('ignore.as.group:some-artifact2:1.0-SNAPSHOT')
        builder.addModule('some.group:ignore-from-extension:1.0-SNAPSHOT')
        def dependencies = """
             implementation nebulaPublishVerification.ignore('some.group:ignore-as-string:1.0-SNAPSHOT')
             implementation nebulaPublishVerification.ignore(group: 'some.group', name: 'ignore-as-map', version: '1.0-SNAPSHOT')
             implementation 'ignore.as.group:some-artifact:1.0-SNAPSHOT'
             implementation 'ignore.as.group:some-artifact2:1.0-SNAPSHOT'
             implementation 'some.group:ignore-from-extension:1.0-SNAPSHOT'
        """

        buildFile << createBuildFileFromTemplate(projectStatus, dependencies, builder)
        buildFile << """
            nebulaPublishVerification {
                ignoreGroup 'ignore.as.group'
                ignore 'some.group:ignore-from-extension:1.0-SNAPSHOT'
            }
        """

        when:
        def result = runTasksSuccessfully('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        result.standardOutput.contains(":verifyPublication")
        result.standardOutput.contains(":publishNebulaIvyPublicationToDistIvyRepository")
    }

    def 'ignored dependencies are not verified - all projects'() {
        given:
        def projectStatus = 'release'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule('some.group:ignore-as-string:1.0-SNAPSHOT')
        builder.addModule('some.group:ignore-as-map:1.0-SNAPSHOT')
        builder.addModule('ignore.as.group:some-artifact:1.0-SNAPSHOT')
        builder.addModule('ignore.as.group:some-artifact2:1.0-SNAPSHOT')
        builder.addModule('some.group:ignore-from-extension:1.0-SNAPSHOT')
        def dependencies = """
             implementation nebulaPublishVerification.ignore('some.group:ignore-as-string:1.0-SNAPSHOT')
             implementation nebulaPublishVerification.ignore(group: 'some.group', name: 'ignore-as-map', version: '1.0-SNAPSHOT')
             implementation 'ignore.as.group:some-artifact:1.0-SNAPSHOT'
             implementation 'ignore.as.group:some-artifact2:1.0-SNAPSHOT'
             implementation 'some.group:ignore-from-extension:1.0-SNAPSHOT'
        """

        DependencyGraph graph = builder.build()
        def generator = new GradleDependencyGenerator(graph, new File(projectDir, "testrepogen").canonicalPath)
        File mavenRepoDir = generator.generateTestMavenRepo()

        File jsonRulesFile = new File(projectDir, 'local-rules.json')
        String rules = this.getClass().getResourceAsStream('/nebula/plugin/publishing/verification/recommendation-rules.json').text
        jsonRulesFile.text = rules

        addSubproject("lib")


        buildFile << """
            allprojects {
                ${applyPlugin(IvyPublishPlugin)}
                ${applyPlugin(MavenPublishPlugin)}
                ${applyPlugin(PublishVerificationPlugin)}
                ${applyPlugin(ResolutionRulesPlugin)}
                ${applyPlugin(DependencyLockPlugin)}
                ${applyPlugin(DependencyRecommendationsPlugin)}
                apply plugin: 'java'
    
                group = 'test.nebula.netflix'                       
                version = '1.0'
                status = '${projectStatus}' 
                
                           
                repositories {
                    maven {
                        url "file://$mavenRepoDir.canonicalPath"
                    }
                }
               
                dependencies {
                    ${dependencies}
                } 
    
                ${publishingRepos()}

                nebulaPublishVerification {
                    ignoreGroup 'ignore.as.group'
                    ignore 'some.group:ignore-from-extension:1.0-SNAPSHOT'
                }
            }
            
            dependencies {
                resolutionRules files('${jsonRulesFile}')
            } 
        """

        when:
        def result = runTasksSuccessfully('build', 'publishNebulaPublicationToDistMavenRepository')

        then:
        result.standardOutput.contains(":lib:verifyPublication")
        result.standardOutput.contains(":lib:publishNebulaPublicationToDistMavenRepository")
        result.standardOutput.contains(":verifyPublication")
        result.standardOutput.contains(":publishNebulaPublicationToDistMavenRepository")
    }

    def 'should work with multi project build'() {
        given:
        def projectStatus = 'release'
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def expectedFailureDependency2 = 'foo:bar2:1.0-SNAPSHOT'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        builder.addModule(expectedFailureDependency2)
        DependencyGraph graph = builder.build()
        def generator = new GradleDependencyGenerator(graph)
        File mavenRepoDir = generator.generateTestMavenRepo()

        buildFile << """   
            allprojects {        
                ${applyPlugin(IvyPublishPlugin)}
                ${applyPlugin(PublishVerificationPlugin)}
                apply plugin: 'java'
                
                group = 'test.nebula.netflix'
                status = '$projectStatus'            
                version = '1.0'
                
                           
                repositories {
                    maven {
                        url "file://$mavenRepoDir.canonicalPath"
                    }
                }
                
                ${publishingRepos()}
            }
        """

        addSubproject('common', """
            dependencies {
                implementation '$expectedFailureDependency'
            }
        """)
        addSubproject('consumer', """
        dependencies {
           implementation project(':common')
           implementation '$expectedFailureDependency2'
        }
        """)

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
        assertStatusFailureMessage(result, expectedFailureDependency2, projectStatus)

    }

    def 'should work with multi project build using parallel build'() {
        given:
        def projectStatus = 'release'
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        def expectedFailureDependency2 = 'foo:bar2:1.0-SNAPSHOT'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        builder.addModule(expectedFailureDependency2)
        DependencyGraph graph = builder.build()
        def generator = new GradleDependencyGenerator(graph)
        File mavenRepoDir = generator.generateTestMavenRepo()

        buildFile << """   
            allprojects {        
                ${applyPlugin(IvyPublishPlugin)}
                ${applyPlugin(PublishVerificationPlugin)}
                apply plugin: 'java'
                
                group = 'test.nebula.netflix'
                status = '$projectStatus'            
                version = '1.0'
                
                           
                repositories {
                    maven {
                        url "file://$mavenRepoDir.canonicalPath"
                    }
                }
                
                ${publishingRepos()}
            }
        """

        addSubproject('common', """
            dependencies {
                implementation '$expectedFailureDependency'
            }
        """)
        addSubproject('consumer', """
        dependencies {
           implementation project(':common')
           implementation '$expectedFailureDependency2'
        }
        """)

        settingsFile.text << """
            org.gradle.parallel=true
        """

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
        assertStatusFailureMessage(result, expectedFailureDependency2, projectStatus)

    }

    def 'should work with java-library plugin'() {
        given:
        def projectStatus = 'release'
        def expectedFailureDependency = 'foo:bar:1.0-SNAPSHOT'
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(expectedFailureDependency)
        DependencyGraph graph = builder.build()
        def generator = new GradleDependencyGenerator(graph)
        File mavenRepoDir = generator.generateTestMavenRepo()

        buildFile << """           
            ${applyPlugin(IvyPublishPlugin)}
            ${applyPlugin(MavenPublishPlugin)}
            ${applyPlugin(PublishVerificationPlugin)} 
            ${applyPlugin(ResolutionRulesPlugin)}
            ${applyPlugin(DependencyLockPlugin)}
            ${applyPlugin(DependencyRecommendationsPlugin)}
            apply plugin: 'java-library'
         
            group = 'test.nebula.netflix'
            status = '$projectStatus'            
            version = '1.0'
            
                       
            repositories {
                maven {
                    url "file://$mavenRepoDir.canonicalPath"
                }
            }
           
            dependencies {
                implementation '$expectedFailureDependency'
            } 
            
            ${publishingRepos()}
        """

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStatusFailureMessage(result, expectedFailureDependency, projectStatus)
    }

    def 'should configure with java-base'() {
        given:

        buildFile << """           
            ${applyPlugin(IvyPublishPlugin)}
            ${applyPlugin(MavenPublishPlugin)}
            ${applyPlugin(PublishVerificationPlugin)}
            apply plugin: 'java-base'
         
            group = 'test.nebula.netflix'            
            version = '1.0'
            ${publishingRepos()}
        """

        when:
        runTasksSuccessfully('build')

        then:
        noExceptionThrown()
    }

    def 'unresolved dependencies should fail fast with clear message'() {
        given:
        def unresolvableDependency = 'unknown:unknown:1.0-SNAPSHOT'
        buildFile << """           
            ${applyPlugin(IvyPublishPlugin)}
            ${applyPlugin(PublishVerificationPlugin)}
            apply plugin: 'java'
         
            group = 'test.nebula.netflix'
            status = 'integration'            
            version = '1.0'

            dependencies {
                implementation '$unresolvableDependency'
            }
            
            ${publishingRepos()}
        """

        when:
        def result = runTasksWithFailure('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        assertStandardOutputOrError(result, "Cannot resolve external dependency $unresolvableDependency")
    }

    def 'composite build should not break the plugin'() {
        given:
        def projectName = projectDir.name

        def includedProjectRootDir = new File(projectDir.parentFile, projectName + "-composite")
        includedProjectRootDir.mkdir()
        def includedProjectBuildFile = new File(includedProjectRootDir, 'build.gradle')

        includedProjectBuildFile << """
            apply plugin: 'java'
            
            group = 'test.nebula.netflix'           
            version = '1.0'
        """

        def includedProjectSettingsFile = new File(includedProjectRootDir, 'settings.gradle')
        includedProjectSettingsFile.text = """\
            rootProject.name='included-project'          
        """

        buildFile << """
            ${applyPlugin(IvyPublishPlugin)}
            ${applyPlugin(PublishVerificationPlugin)}
            apply plugin: 'java'
            
            group = 'test.nebula.netflix'           
            version = '1.0'
            
            dependencies {
                implementation 'test.nebula.netflix:included-project:1.0'
            }

            ${publishingRepos()}
        """

        settingsFile.text = """\
            rootProject.name='consuming-project'
            includeBuild('$includedProjectRootDir')
        """

        when:
        runTasksSuccessfully('build', 'publishNebulaIvyPublicationToDistIvyRepository')

        then:
        noExceptionThrown()
    }

    @IgnoreIf({ Boolean.valueOf(env["NEBULA_IGNORE_TEST"]) })
    def 'should work with custom ComponentMetadataSupplier'() {
        given:
        writeHelloWorld('test.nebula.netflix')
        buildFile << """           
            ${applyPlugin(PublishVerificationPlugin)}         
            apply plugin: 'java'

            group = 'test.nebula.netflix'                       
            version = '1.0'            
                       
            repositories {
                maven {
                    metadataSupplier = CustomMetadataSupplier.class
                    url "https://repo.maven.apache.org/maven2/" //This usually would be an internal repository
                }
            }
            
            dependencies {
                implementation 'org.slf4j:slf4j-api:latest.release'
            }


            class CustomMetadataSupplier implements ComponentMetadataSupplier {
            
                static final SNAPSHOT_VERSION = ~/(?i).+(-|\\.)(ALPHA|SNAPSHOT|PR|DEV).*/
                static final CANDIDATE_VERSION = ~/(?i).+(-|\\.)(BETA|CANDIDATE|CR|RC).*/
            
                @Override
                void execute(ComponentMetadataSupplierDetails details) {
                    def id = details.getId()
                    String status
                    if (id.version =~ CANDIDATE_VERSION) {
                        status = 'milestone'
                    }
                    else if (id.version =~ SNAPSHOT_VERSION) {
                        status = 'integration'
                    } else {
                        status = 'release'
                    }
                    details.getResult().setStatus(status)
                }
            }
        """

        when:
        def result = runTasksSuccessfully('dependencies')

        then:
        result.standardOutput.contains("org.slf4j:slf4j-api:latest.release ->")
        !result.standardOutput.contains("-alpha")
        !result.standardOutput.contains("-beta")
    }

    private String createBuildFileFromTemplate(String projectStatus, String dependencies, DependencyGraphBuilder builder) {
        DependencyGraph graph = builder.build()
        def generator = new GradleDependencyGenerator(graph, new File(projectDir, "testrepogen").canonicalPath)
        File mavenRepoDir = generator.generateTestMavenRepo()

        File jsonRulesFile = new File(projectDir, 'local-rules.json')
        String rules = this.getClass().getResourceAsStream('/nebula/plugin/publishing/verification/recommendation-rules.json').text
        jsonRulesFile.text = rules

        """           
            ${applyPlugin(IvyPublishPlugin)}
            ${applyPlugin(MavenPublishPlugin)}
            ${applyPlugin(PublishVerificationPlugin)}
            ${applyPlugin(ResolutionRulesPlugin)}
            ${applyPlugin(DependencyLockPlugin)}
            ${applyPlugin(DependencyRecommendationsPlugin)}
            apply plugin: 'java'

            group = 'test.nebula.netflix'                       
            version = '1.0'
            status = '${projectStatus}' 
            
                       
            repositories {
                maven {
                    url "file://$mavenRepoDir.canonicalPath"
                }
            }
           
            dependencies {
                resolutionRules files('${jsonRulesFile}')
                ${dependencies}
            } 

            ${publishingRepos()}
        """
    }

    private String publishingRepos() {
        """
        publishing {
            repositories {
                ivy {
                    name 'distIvy'
                    url project.file("\${project.layout.buildDirectory.getAsFile().get()}/distIvy").toURI().toURL()
                }
                maven {
                    name 'distMaven'
                    url project.file("\${project.layout.buildDirectory.getAsFile().get()}/distMaven").toURI().toURL()
                }
            }
        }
        """
    }

    private void assertStatusFailureMessage(ExecutionResult result, String expectedFailureDependency, String projectStatus) {
        int lastColon = expectedFailureDependency.lastIndexOf(':')
        String groupAndName = expectedFailureDependency.substring(0, lastColon)
        String version = expectedFailureDependency.substring(lastColon + 1, expectedFailureDependency.size())
        assertStandardOutputOrError(result, "Following dependencies have incorrect status lower then your current project status '$projectStatus':")
        assertStandardOutputOrError(result, "'$groupAndName' resolved to version '${version}', status: 'integration' in status scheme: [integration, milestone, release]")
    }

    private void assertVersionFailureMessage(ExecutionResult result, String expectedFailureDependency) {
        int lastColon = expectedFailureDependency.lastIndexOf(':')
        String groupAndName = expectedFailureDependency.substring(0, lastColon)
        String version = expectedFailureDependency.substring(lastColon + 1, expectedFailureDependency.size())
        assertStandardOutputOrError(result, "Following dependencies have version definition with patterns which resolves into unexpected version.")
        assertStandardOutputOrError(result, "'$groupAndName' with requested version '${version}'")
    }

    private void assertStandardOutputOrError(ExecutionResult result, String message) {
        //output where message is printed is different between Gradle 4.7 and 4.8 while we are testing Gradle 4.8 we need to check both
        assert result.standardError.contains(message) || result.standardOutput.contains(message)
    }
}
