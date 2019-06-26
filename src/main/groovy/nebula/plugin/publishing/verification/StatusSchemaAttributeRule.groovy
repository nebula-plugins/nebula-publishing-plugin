package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentMetadataRule


//@CacheableRule TODO: this is disable to test RealisedMavenModuleResolveMetadataSerializationHelper duplicate objects
class StatusSchemaAttributeRule implements ComponentMetadataRule {
    @Override
    void execute(ComponentMetadataContext componentMetadataContext) {
        modifyAttributes(componentMetadataContext.details)
    }

    static void modifyAttributes(ComponentMetadataDetails details) {
        //TODO: This should probably be replaced with a proper public API
        if (details.class.name.contains('ShallowComponentMetadataAdapter') || details.class.name.contains('DefaultComponentMetadataProcessor')) {
            return
        }
        details.attributes {
            attribute PublishVerificationPlugin.STATUS_SCHEME, details.statusScheme.join(',')
        }
    }
}
