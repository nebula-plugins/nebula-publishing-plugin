package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentMetadataRule


@CacheableRule
class StatusSchemaAttributeRule implements ComponentMetadataRule {
    @Override
    void execute(ComponentMetadataContext componentMetadataContext) {
        modifyAttributes(componentMetadataContext.details)
    }

    static void modifyAttributes(ComponentMetadataDetails details) {
        details.attributes {
            attribute PublishVerificationPlugin.STATUS_SCHEME, details.statusScheme.join(',')
        }
    }
}
