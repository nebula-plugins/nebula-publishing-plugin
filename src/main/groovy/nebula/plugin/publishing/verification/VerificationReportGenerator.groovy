package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.ModuleVersionIdentifier

class VerificationReportGenerator {
    String generateReport(Map<String, List<StatusVerificationViolation>> violationsPerProject, String targetStatus) {
        StringBuilder builder = new StringBuilder()
        builder.append("Following dependencies have incorrect status lower then your current project status '${targetStatus}':\n")
        violationsPerProject.each { key, value ->
            if (value.size() > 0) {
                builder.append("Dependencies for ${key}:\n")
                value.each {
                    builder.append("    '${it.id.group}:${it.id.name}' resolved to version '${it.id.version}'," +
                            " status: '${it.metadata.status}' in status scheme: ${it.metadata.statusScheme}\n")
                }
            }
        }
        builder.append('\n')
        builder.append("*** OPTIONS ***\n")
        builder.append("1) Use a specific module version with higher status or 'latest.${targetStatus}'.\n")
        builder.append("2) Ignore this check with the following build.gradle configurations.\n")
        builder.append('\n')

        if (violationsPerProject.size() == 1) {
            builder.append("You have a single module project - place following configuration after plugins section in your project build.gradle file\n\n")
            builder.append("nebulaPublishVerification {\n")
            builder.append(violationsPerProject.values().first().collect { "    ignore('${it.id.module}')\n"}.join('') )
            builder.append("}\n")

        } else {
            builder.append("You have a multi module project - place following configuration after plugins section in your root project build.gradle file\n\n")
            builder.append("allprojects {\n")
            builder.append("    nebulaPublishVerification {\n")
            Set<ModuleVersionIdentifier> modulesForIgnore = violationsPerProject.values().flatten().collect{ it.id }.toSet()
            builder.append(modulesForIgnore.collect { "        ignore('${it.module}')\n"}.join('') )
            builder.append("    }\n")
            builder.append("}\n")
        }
        builder.toString()
    }
}
