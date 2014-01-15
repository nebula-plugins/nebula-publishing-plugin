package nebula.plugin.publishing.ivy

import org.semver.Version

import java.util.regex.Matcher

/**
 * Copied from netflix.idunn.version.VersionFactory
 */
class VersionFactory {
    static Version create(String dirtyRevision) {
        def cleanRevision = dirtyRevision.replaceAll('_', '+')

        Matcher revMatcher = cleanRevision =~ /^r?(\d+)$/
        if (revMatcher) {
            def major = Integer.parseInt(revMatcher[0][1])
            cleanRevision = "${major}.0"
        }
        Matcher fourStyle = cleanRevision =~ /^(\d+)\.(\d+)\.(\d+)\.(\d+)$/
        if (fourStyle) {
            cleanRevision = "${fourStyle[0][1]}.${fourStyle[0][2]}.${fourStyle[0][3]}+${fourStyle[0][4]}"
        }
        Matcher dateStyle = cleanRevision =~ /^(\d{4})-(\d\d)-(\d\d)$/
        if (dateStyle) {
            cleanRevision = "${dateStyle[0][1]}.${dateStyle[0][2]}.${dateStyle[0][3]}"
        }
        Matcher projectPrefixStyle = cleanRevision =~ /^\w+-(.*)$/
        if (projectPrefixStyle) {
            cleanRevision = projectPrefixStyle[0][1]
        }

        Version.parse(cleanRevision)
    }
}