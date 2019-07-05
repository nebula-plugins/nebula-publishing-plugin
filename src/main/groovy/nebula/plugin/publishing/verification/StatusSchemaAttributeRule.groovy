package nebula.plugin.publishing.verification

import groovy.transform.CompileStatic
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.attributes.Attribute


//@CacheableRule TODO: this is disable to test RealisedMavenModuleResolveMetadataSerializationHelper duplicate objects
@CompileStatic
class StatusSchemaAttributeRule implements ComponentMetadataRule {
    public static final Attribute<String> STATUS_SCHEME = Attribute.of('org.my.internal.statusScheme', String)

    @Override
    void execute(ComponentMetadataContext componentMetadataContext) {
        ComponentMetadataDetails details = componentMetadataContext.details
        details.attributes {
            it.attribute STATUS_SCHEME, details.statusScheme.join(',')
        }
    }
}
