package nebula.plugin.publishing.ivy

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import spock.lang.Specification

class ResolvedIvyPluginSpec extends Specification {

    def 'extract confs'() {
        def result
        
        when:
        result = IvyResolvedDependenciesPlugin.extractConfs('compile->compile')

        then:
        result == ['compile'] as Set
        
        when:
        result = IvyResolvedDependenciesPlugin.extractConfs('runtime,provided->*')

        then:
        result == ['runtime', 'provided'] as Set

        when:
        result = IvyResolvedDependenciesPlugin.extractConfs('runtime->master;test->*')

        then:
        result == ['runtime', 'test'] as Set
    }
    
    def 'ensure equals of DefaultModuleIdentifier'() {
        def left = new DefaultModuleIdentifier('name', 'group')
        def right = new DefaultModuleIdentifier('name', 'group')
        
        expect:
        left.equals(right)
        
        when:
        def map = [:]
        map.put(left, 'VALUE')
        
        then:
        map[right] == 'VALUE'
    }
}

