package nebula.plugin.publishing.verification

import groovy.transform.CompileStatic
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentMetadataRule


//@CacheableRule TODO: this is disable to test RealisedMavenModuleResolveMetadataSerializationHelper duplicate objects
@CompileStatic
class StatusSchemaAttributeRule implements ComponentMetadataRule {
    @Override
    void execute(ComponentMetadataContext componentMetadataContext) {
        modifyAttributes(componentMetadataContext.details)
    }

    static void modifyAttributes(ComponentMetadataDetails details) {
        details.attributes {
            it.attribute(PublishVerificationPlugin.STATUS_SCHEME, details.statusScheme.join(','))
        }
    }
}
