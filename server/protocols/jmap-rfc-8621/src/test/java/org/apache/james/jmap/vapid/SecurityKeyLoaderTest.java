/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.vapid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.filesystem.api.FileSystemFixture;
import org.apache.james.jmap.core.JmapRfc8621Configuration;
import org.junit.jupiter.api.Test;

public class SecurityKeyLoaderTest {
    @Test
    void loadShouldThrowWhenVapidIsNotEnabled() {
        JmapRfc8621Configuration jmapConfiguration = JmapRfc8621Configuration.LOCALHOST_CONFIGURATION();

        SecurityKeyLoader loader = new SecurityKeyLoader(
            FileSystemFixture.CLASSPATH_FILE_SYSTEM,
            jmapConfiguration);

        assertThatThrownBy(loader::loadAsymmetricKeys)
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Vapid authentication is not enabled");
    }

    @Test
    void loadShouldReturnAsymmetricKeys() throws Exception {
        JmapRfc8621Configuration jmapConfiguration = jmapWithVapidConfiguration();

        SecurityKeyLoader loader = new SecurityKeyLoader(
            FileSystemFixture.CLASSPATH_FILE_SYSTEM,
            jmapConfiguration);

        assertThat(loader.loadAsymmetricKeys())
            .isNotNull();
    }

    //TODO: add a few more test cases?

    private JmapRfc8621Configuration jmapWithVapidConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("webpush.vapid.auth.enabled", "true");
        configuration.addProperty("webpush.vapid.private.key", "vapid.private.key");
        configuration.addProperty("webpush.vapid.public.key", "vapid.public.key");

        return JmapRfc8621Configuration.from(configuration);
    }
}
