/*
 * Copyright 2015-2025 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.publishing.maven

import groovy.xml.XmlSlurper
import java.util.jar.JarFile

/**
 * Integration tests for the shadow component feature flag (nebula.publishing.features.detectShadowComponent.enabled)
 * Tests in this class verify the new behavior when the feature flag is enabled.
 */
class MavenShadowComponentFeatureIntegrationSpec extends BaseMavenNebulaShadowJarPublishPluginIntegrationSpec {
    String shadowPluginId = 'com.gradleup.shadow'
    String shadowPluginVersion = '8.3.7'

    def 'shadow jar contains relocated dependencies'() {
        setup:
        def propsFile = new File(projectDir, 'gradle.properties')
        propsFile.text = 'nebula.publishing.features.detectShadowComponent.enabled=true'

        buildFile << """
            jar {
                enabled = false
            }

            shadowJar {
                archiveClassifier.set(null)
                relocate 'com.google', 'com.netflix.shading.google'
            }
        """
        writeJavaSourceFile("""
package demo;
import com.google.common.collect.ImmutableList;

public class DemoApplication {
    public static void main(String[] args) {
        ImmutableList<String> list = ImmutableList.of("test");
        System.out.println(list);
    }
}
""")

        when:
        runTasks('shadowJar', 'publishNebulaPublicationToTestLocalRepository')

        then:
        def jarFile = new File(projectDir, 'testrepo/test/nebula/mavenpublishingtest/0.1.0/mavenpublishingtest-0.1.0.jar')
        jarFile.exists()

        when:
        def jar = new JarFile(jarFile)
        def entries = jar.entries().collect { it.name }
        jar.close()

        then:
        entries.any { it.contains('com/netflix/shading/google') }
        !entries.any { it.startsWith('com/google/common') }
    }

    def 'publish shadow jar with multiple external dependencies in POM'() {
        setup:
        def propsFile = new File(projectDir, 'gradle.properties')
        propsFile.text = 'nebula.publishing.features.detectShadowComponent.enabled=true'

        buildFile << """
            dependencies {
                implementation 'com.google.code.gson:gson:2.8.9'
                shadow 'commons-io:commons-io:2.11.0'
                shadow 'org.apache.commons:commons-lang3:3.12.0'
            }

            jar {
                enabled = false
            }

            shadowJar {
                archiveClassifier.set(null)
                relocate 'com.google.common', 'shaded.guava.common'
                relocate 'com.google.gson', 'shaded.gson'
            }
        """
        writeJavaSourceFile("""
package demo;
import com.google.gson.Gson;

public class DemoApplication {
    public static void main(String[] args) {
        Gson gson = new Gson();
        System.out.println(gson.toJson("Hello"));
    }
}
""")

        when:
        runTasks('shadowJar', 'publishNebulaPublicationToTestLocalRepository')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        def dependencies = pom.dependencies.dependency

        // External dependencies (in shadow config) should be in POM
        dependencies.size() == 2
        dependencies.find { it.artifactId == 'commons-io' }.version == '2.11.0'
        dependencies.find { it.artifactId == 'commons-lang3' }.version == '3.12.0'

        // Bundled dependencies (in implementation) should NOT be in POM
        !dependencies.find { it.artifactId == 'guava' }
        !dependencies.find { it.artifactId == 'gson' }

        when:
        def jarFile = new File(projectDir, 'testrepo/test/nebula/mavenpublishingtest/0.1.0/mavenpublishingtest-0.1.0.jar')
        def jar = new JarFile(jarFile)
        def entries = jar.entries().collect { it.name }
        jar.close()

        then:
        // Bundled dependencies should be relocated in the jar
        entries.any { it.contains('shaded/guava/common') }
        entries.any { it.contains('shaded/gson') }
        !entries.any { it.startsWith('com/google/common') }
        !entries.any { it.startsWith('com/google/gson') }

        // External dependencies should NOT be in the jar
        !entries.any { it.startsWith('org/apache/commons/io') }
        !entries.any { it.startsWith('org/apache/commons/lang3') }

        and:
        fileWasPublished('mavenpublishingtest-0.1.0.jar')
        fileWasPublished('mavenpublishingtest-0.1.0.pom')
    }

    def 'publication uses shadow component instead of java component'() {
        setup:
        def propsFile = new File(projectDir, 'gradle.properties')
        propsFile.text = 'nebula.publishing.features.detectShadowComponent.enabled=true'

        buildFile << """
            jar {
                enabled = false
            }

            shadowJar {
                archiveClassifier.set(null)
                relocate 'com.google', 'com.netflix.shading.google'
            }
        """
        writeJavaSourceFile("""
package demo;

public class DemoApplication {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
""")

        when:
        def result = runTasks('shadowJar', 'publishNebulaPublicationToTestLocalRepository', '--info')

        then:
        result.output.contains('shadow')
        fileWasPublished('mavenpublishingtest-0.1.0.jar')

        and:
        def jarFile = new File(projectDir, 'testrepo/test/nebula/mavenpublishingtest/0.1.0/mavenpublishingtest-0.1.0.jar')
        jarFile.exists()
        jarFile.size() > 0
    }

    def 'dependencies in shadow configuration appear in POM but not in jar'() {
        setup:
        def propsFile = new File(projectDir, 'gradle.properties')
        propsFile.text = 'nebula.publishing.features.detectShadowComponent.enabled=true'

        buildFile << """
            dependencies {
                shadow 'commons-io:commons-io:2.11.0'
            }

            jar {
                enabled = false
            }

            shadowJar {
                archiveClassifier.set(null)
                relocate 'com.google', 'com.netflix.shading.google'
            }
        """
        writeJavaSourceFile("""
package demo;

public class DemoApplication {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
""")

        when:
        runTasks('shadowJar', 'publishNebulaPublicationToTestLocalRepository')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        def dependencies = pom.dependencies.dependency

        // Commons-io should be in POM (it's in shadow config, so it's an external dependency)
        def commonsIo = dependencies.find { it.artifactId == 'commons-io' }
        commonsIo != null
        commonsIo.version == '2.11.0'

        when:
        def jarFile = new File(projectDir, 'testrepo/test/nebula/mavenpublishingtest/0.1.0/mavenpublishingtest-0.1.0.jar')
        def jar = new JarFile(jarFile)
        def entries = jar.entries().collect { it.name }
        jar.close()

        then:
        // Commons-io should NOT be bundled in the jar (it's in shadow config, so it's external)
        !entries.any { it.startsWith('org/apache/commons/io') }

        // Guava should be bundled and relocated (it's in implementation and relocated)
        entries.any { it.contains('com/netflix/shading/google') }
    }

    def 'gradle module metadata is correct with shadow component'() {
        setup:
        def propsFile = new File(projectDir, 'gradle.properties')
        propsFile.text = 'nebula.publishing.features.detectShadowComponent.enabled=true'

        buildFile << """
            dependencies {
                // External dependency - should appear in module metadata
                shadow 'commons-io:commons-io:2.11.0'

                // Bundled dependencies - should NOT appear in module metadata
                implementation 'com.google.code.gson:gson:2.8.9'
                implementation 'org.apache.commons:commons-lang3:3.12.0'
            }

            jar {
                enabled = false
            }

            shadowJar {
                archiveClassifier.set(null)
                relocate 'com.google.common', 'shaded.guava.common'
                relocate 'com.google.gson', 'shaded.gson'
                relocate 'org.apache.commons', 'shaded.commons'
            }
        """
        writeJavaSourceFile("""
package demo;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;

public class DemoApplication {
    public static void main(String[] args) {
        Gson gson = new Gson();
        System.out.println(gson.toJson("Hello"));
        System.out.println(StringUtils.capitalize("world"));
    }
}
""")

        when:
        runTasks('shadowJar', 'publishNebulaPublicationToTestLocalRepository')

        then:
        def moduleFile = new File(projectDir, 'testrepo/test/nebula/mavenpublishingtest/0.1.0/mavenpublishingtest-0.1.0.module')
        moduleFile.exists()

        when:
        def moduleJson = new groovy.json.JsonSlurper().parse(moduleFile)

        then:
        // Verify module component metadata
        moduleJson.formatVersion == '1.1'
        moduleJson.component.group == 'test.nebula'
        moduleJson.component.module == 'mavenpublishingtest'
        moduleJson.component.version == '0.1.0'

        // Verify variants exist
        moduleJson.variants.size() > 0

        // Check runtime variant
        def runtimeVariant = moduleJson.variants.find { it.name.contains('runtime') || it.name.contains('Runtime') }
        runtimeVariant != null

        // External dependency (commons-io in shadow config) SHOULD be in module metadata
        def commonsIoDep = runtimeVariant.dependencies?.find { it.module == 'commons-io' }
        commonsIoDep != null
        commonsIoDep.group == 'commons-io'
        commonsIoDep.version.requires == '2.11.0'

        // Bundled dependencies (implementation with relocation) should NOT be in module metadata
        def gsonDep = runtimeVariant.dependencies?.find { it.module == 'gson' }
        gsonDep == null
        def guavaDep = runtimeVariant.dependencies?.find { it.module == 'guava' }
        guavaDep == null
        def commonsLang3Dep = runtimeVariant.dependencies?.find { it.module == 'commons-lang3' }
        commonsLang3Dep == null

        and:
        // Verify the shadow jar artifact is properly referenced
        def runtimeElements = moduleJson.variants.find { it.name == 'shadowRuntimeElements' || it.name.contains('runtime') }
        runtimeElements != null
        runtimeElements.files?.size() > 0

        // Verify the artifact filename
        def jarArtifact = runtimeElements.files?.find { it.name == 'mavenpublishingtest-0.1.0.jar' }
        jarArtifact != null
        jarArtifact.url == 'mavenpublishingtest-0.1.0.jar'

        when:
        // Verify the actual jar contains bundled dependencies (relocated)
        def jarFile = new File(projectDir, 'testrepo/test/nebula/mavenpublishingtest/0.1.0/mavenpublishingtest-0.1.0.jar')
        def jar = new JarFile(jarFile)
        def entries = jar.entries().collect { it.name }
        jar.close()

        then:
        // Bundled dependencies should be relocated in the jar
        entries.any { it.contains('shaded/guava/common') }
        entries.any { it.contains('shaded/gson') }
        entries.any { it.contains('shaded/commons') }

        // Original packages should NOT exist
        !entries.any { it.startsWith('com/google/common') }
        !entries.any { it.startsWith('com/google/gson') }
        !entries.any { it.startsWith('org/apache/commons/lang3') }

        // External dependency should NOT be in the jar
        !entries.any { it.startsWith('org/apache/commons/io') }
    }

    def 'feature flag works via command line property'() {
        setup:
        // No gradle.properties file - feature flag will come from command line
        buildFile << """
            dependencies {
                shadow 'commons-io:commons-io:2.11.0'
            }

            jar {
                enabled = false
            }

            shadowJar {
                archiveClassifier.set(null)
                relocate 'com.google', 'com.netflix.shading.google'
            }
        """
        writeJavaSourceFile("""
package demo;

public class DemoApplication {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
""")

        when:
        def result = runTasks('shadowJar', 'publishNebulaPublicationToTestLocalRepository',
            '-Pnebula.publishing.features.detectShadowComponent.enabled=true',
            '--info')

        then:
        result.output.contains('shadow')
        fileWasPublished('mavenpublishingtest-0.1.0.jar')
        fileWasPublished('mavenpublishingtest-0.1.0.pom')

        and:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        def dependencies = pom.dependencies.dependency
        def commonsIo = dependencies.find { it.artifactId == 'commons-io' }
        commonsIo != null
    }
}
