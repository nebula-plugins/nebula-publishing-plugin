package nebula.plugin.publishing

import nebula.test.IntegrationTestKitSpec

abstract class BaseIntegrationTestKitSpec extends IntegrationTestKitSpec {
    def setup() {
        // Enable configuration cache :)
        new File(projectDir, 'gradle.properties') << '''org.gradle.configuration-cache=true'''.stripIndent()
    }

    /** Creates real SCM metadata inside this fixture, independent of the checkout running the tests. */
    protected void initializeGitRepository(String origin) {
        runGit('init', '-b', 'main')
        runGit('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.org',
                '-c', 'commit.gpgsign=false', 'commit', '--allow-empty', '-m', 'Fixture')
        runGit('remote', 'add', 'origin', origin)
    }

    private void runGit(String... arguments) {
        List<String> command = ['git', '-C', projectDir.absolutePath]
        command.addAll(arguments)
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start()
        String output = process.inputStream.text
        assert process.waitFor() == 0: output
    }

    void disableConfigurationCache() {
        def propertiesFile = new File(projectDir, 'gradle.properties')
        if(propertiesFile.exists()) {
            propertiesFile.delete()
        }
        propertiesFile.createNewFile()
        propertiesFile << '''org.gradle.configuration-cache=false'''.stripIndent()
    }
}
