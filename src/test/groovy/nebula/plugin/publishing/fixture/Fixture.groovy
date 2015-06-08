package nebula.plugin.publishing.fixture

import java.util.concurrent.atomic.AtomicBoolean

class Fixture {
    static AtomicBoolean created = new AtomicBoolean(false)
    static String repo = new File('build/testrepogen/mavenrepo').absolutePath


}
