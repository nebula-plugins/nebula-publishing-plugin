package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier

class PublishVerificationExtension {

    Set<ModuleIdentifier> ignore = new HashSet<>()

    String ignore(String dependency) {
        String[] split = dependency.split(':')
        if (split.length < 2 || split.length > 3) {
           throw new IllegalArgumentException("Dependency $dependency has incorrect format." +
                   " We expect 'group:artifactId:version' or 'group:artifactId'.")
        }
        ignore << DefaultModuleIdentifier.newId(split[0], split[1])
        dependency
    }

    Map ignore(Map<String, String> dependency) {
        if (! dependency.containsKey('group') || ! dependency.containsKey('name')) {
            throw new IllegalArgumentException("Dependency $dependency has incorrect format. We expect" +
                    " group: 'group', name: 'artifactId' or group: 'group', name: 'artifactId', version: 'version'.")
        }
        ignore << DefaultModuleIdentifier.newId(dependency.group, dependency.name)
        dependency
    }
}
