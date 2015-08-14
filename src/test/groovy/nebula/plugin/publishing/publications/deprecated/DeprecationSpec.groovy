package nebula.plugin.publishing.publications.deprecated

import nebula.plugin.publishing.publications.JavadocJarPlugin
import nebula.plugin.publishing.publications.SourceJarPlugin
import nebula.plugin.publishing.publications.TestJarPlugin
import nebula.test.ProjectSpec
import org.gradle.api.logging.Logger

class DeprecationSpec extends ProjectSpec {
    def 'old javadoc plugin applies new one'() {
        when:
        project.plugins.apply 'nebula-javadoc-jar'

        then:
        project.plugins.hasPlugin(JavadocJarPlugin)
    }

    def 'javadoc warning message is sent'() {
        given:
        def testLogger = Mock(Logger)
        DeprecatedJavadocJarPlugin.logger = testLogger

        when:
        project.plugins.apply DeprecatedJavadocJarPlugin

        then:
        1 * testLogger.warn(_)
    }

    def 'old source plugin applies new one'() {
        when:
        project.plugins.apply 'nebula-source-jar'

        then:
        project.plugins.hasPlugin(SourceJarPlugin)
    }

    def 'source warning message is sent'() {
        given:
        def testLogger = Mock(Logger)
        DeprecatedSourceJarPlugin.logger = testLogger

        when:
        project.plugins.apply DeprecatedSourceJarPlugin

        then:
        1 * testLogger.warn(_)
    }

    def 'old test plugin applies new one'() {
        when:
        project.plugins.apply 'nebula-test-jar'

        then:
        project.plugins.hasPlugin(TestJarPlugin)
    }

    def 'test warning message is sent'() {
        given:
        def testLogger = Mock(Logger)
        DeprecatedTestJarPlugin.logger = testLogger

        when:
        project.plugins.apply DeprecatedTestJarPlugin

        then:
        1 * testLogger.warn(_)
    }
}
