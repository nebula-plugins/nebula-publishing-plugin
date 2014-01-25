package nebula.plugin.publishing.ivy

import spock.lang.Specification

class VersionFactorySpec extends Specification {
    def 'handle YYYY-MM-DD'() {
        when:
        def version = VersionFactory.create('2012-12-21')

        then:
        noExceptionThrown()
        version.toString() == '2012.12.21'
    }

    def 'fail on YY-MM-DD'() {
        when:
        VersionFactory.create('12-12-21')

        then:
        thrown IllegalArgumentException
    }

    def 'fail on YYYY-M-DD'() {
        when:
        VersionFactory.create('2012-1-21')

        then:
        thrown IllegalArgumentException
    }

    def 'fail on YYYY-MM-D'() {
        when:
        VersionFactory.create('2012-12-1')

        then:
        thrown IllegalArgumentException
    }
}
