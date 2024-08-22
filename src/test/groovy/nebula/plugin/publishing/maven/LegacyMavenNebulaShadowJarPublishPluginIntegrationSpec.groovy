/*
 * Copyright 2015-2024 Netflix, Inc.
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
package nebula.plugin.publishing.maven


/**
 * Used to verify behavior against older plugin id/version of shadow
 */
class LegacyMavenNebulaShadowJarPublishPluginIntegrationSpec extends BaseMavenNebulaShadowJarPublishPluginIntegrationSpec {
    String shadowPluginId = 'com.github.johnrengelman.shadow'
    String shadowPluginVersion = '8.1.1'
}
