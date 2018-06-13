package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier

class VerificationReportGenerator {

    private final static String INDENTATION = "    "

    String generateReport(Map<String, ViolationsContainer> violationsPerProject, String targetStatus) {
        StringBuilder builder = new StringBuilder()
        builder.append(INDENTATION + '\n')
        if (hasAnyStatusViolations(violationsPerProject)) {
            generateStatusViolations(builder, targetStatus, violationsPerProject)
        }
        if (hasAnyVersionSelectorViolations(violationsPerProject)) {
            generateVersionSelectorViolations(builder, violationsPerProject)
        }

        builder.append(INDENTATION + "ATTENTION: If suggested steps for violations cannot be resolved you can ignore them using the following configurations.\n")

        if (violationsPerProject.size() == 1) {
            singleModuleProjectIgnoreBlock(builder, violationsPerProject)

        } else {
            multiModuleProjectIgnoreBlock(builder, violationsPerProject)
        }
        builder.toString()
    }

    private boolean hasAnyStatusViolations(Map<String, ViolationsContainer> violationsPerProject) {
        violationsPerProject.any { it.value.statusViolations.size() > 0 }
    }

    private void generateStatusViolations(StringBuilder builder, String targetStatus, Map<String, ViolationsContainer> violationsPerProject) {
        builder.append(INDENTATION + "Following dependencies have incorrect status lower then your current project status '${targetStatus}':\n")
        violationsPerProject.each { key, value ->
            if (value.statusViolations.size() > 0) {
                builder.append("\n${INDENTATION}Dependencies for ${key}:\n")
                value.statusViolations.each {
                    builder.append(INDENTATION + "    '${it.id.group}:${it.id.name}' resolved to version '${it.id.version}'," +
                            " status: '${it.metadata.status}' in status scheme: ${it.metadata.statusScheme}\n")
                }
            }
        }
        builder.append(INDENTATION + "HOW TO RESOLVE: https://github.com/nebula-plugins/nebula-publishing-plugin#your-dependency-has-a-lower-status-then-your-project-violation\n")
        builder.append(INDENTATION + '\n')
    }

    private boolean hasAnyVersionSelectorViolations(Map<String, ViolationsContainer> violationsPerProject) {
        violationsPerProject.any { it.value.versionSelectorViolations.size() > 0 }
    }

    private void generateVersionSelectorViolations(StringBuilder builder, Map<String, ViolationsContainer> violationsPerProject) {
        builder.append(INDENTATION + "Following dependencies have version definition with patterns which resolves into unexpected version.\n")
        violationsPerProject.each { key, value ->
            if (value.versionSelectorViolations.size() > 0) {
                builder.append("\n${INDENTATION}Dependencies for ${key}:\n")
                value.versionSelectorViolations.each {
                    builder.append(INDENTATION + "    '${it.dependency.group}:${it.dependency.name}' with requested version '${it.dependency.version}'\n")
                }
            }
        }
        builder.append(INDENTATION + "HOW TO RESOLVE: https://github.com/nebula-plugins/nebula-publishing-plugin#your-dependency-has-incorrect-subversion-definition\n")
        builder.append('\n')
    }

    private void singleModuleProjectIgnoreBlock(StringBuilder builder, Map<String, ViolationsContainer> violationsPerProject) {
        builder.append(INDENTATION + "Place following configuration at the end of your project build.gradle file\n\n")
        builder.append(INDENTATION + "nebulaPublishVerification {\n")
        def violationContainer = violationsPerProject.values().first()
        Set<ModuleIdentifier> modulesForIgnore = violationContainer.statusViolations.collect { it.id.module }
        modulesForIgnore += violationContainer.versionSelectorViolations.collect {
            DefaultModuleIdentifier.newId(it.dependency.group, it.dependency.name)
        }
        builder.append(modulesForIgnore.collect { INDENTATION +  "    ignore('${it}')\n" }.join(''))
        builder.append(INDENTATION + "}\n")
    }

    private void multiModuleProjectIgnoreBlock(StringBuilder builder, Map<String, ViolationsContainer> violationsPerProject) {
        builder.append(INDENTATION + "Place following configuration at the end of your root project build.gradle file\n\n")
        builder.append(INDENTATION + "allprojects {\n")
        builder.append(INDENTATION + "    nebulaPublishVerification {\n")
        def violationsContainers = violationsPerProject.values()
        Set<ModuleIdentifier> modulesForIgnore = violationsContainers
                .collect { it.statusViolations }
                .flatten()
                .collect { it.id.module }.toSet()
        modulesForIgnore += violationsContainers
                .collect { it.versionSelectorViolations }
                .flatten()
                .collect { DefaultModuleIdentifier.newId(it.dependency.group, it.dependency.name) }.toSet()
        builder.append(modulesForIgnore.collect { INDENTATION +  "        ignore('${it}')\n" }.join(''))
        builder.append(INDENTATION + "    }\n")
        builder.append(INDENTATION + "}\n")
    }
}
