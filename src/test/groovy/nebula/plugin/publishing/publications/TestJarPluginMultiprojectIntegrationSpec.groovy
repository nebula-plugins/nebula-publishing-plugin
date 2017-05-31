/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.publishing.publications

import nebula.test.IntegrationSpec

@Deprecated
class TestJarPluginMultiprojectIntegrationSpec extends IntegrationSpec{
    def 'allow sibling project to reference created test-jar'() {
        buildFile << '''\
            version = '0.1.0'
            group = 'nebula'
        '''.stripIndent()
        def commonDir = addSubproject('common', """\
            apply plugin: 'java'
            ${applyPlugin(TestJarPlugin)}
        """.stripIndent())

        def commonTest = createFile('src/test/java/example/common/CommonUtil.java', commonDir)
        commonTest.text = '''\
            package example.common;
            public class CommonUtil {
                public String foo() {
                    return "foo";
                }
            }
        '''.stripIndent()

        def clientDir = addSubproject('client', """\
            apply plugin: 'java'
            dependencies {
                testCompile project(path: ':common', configuration: 'test')
            }
        """.stripIndent())

        def clientTest = createFile('src/test/java/example/client/ClientUtil.java', clientDir)
        clientTest.text = '''\
            package example.client;
            import example.common.CommonUtil;
            public class ClientUtil {
                public String bar() {
                    return new CommonUtil().foo();
                }
            }
        '''.stripIndent()

        when:
        runTasksSuccessfully(':client:compileTestJava')

        then:
        noExceptionThrown()
    }
}
