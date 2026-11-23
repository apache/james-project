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

package org.apache.james.imap.processor.main;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.io.FileHandler;
import org.junit.jupiter.api.Test;

class DefaultImapProcessorFactoryTest {
    private static XMLConfiguration imapServers(String servers) throws Exception {
        XMLConfiguration configuration = new XMLConfiguration();
        new FileHandler(configuration).load(new ByteArrayInputStream(("<imapservers>" + servers + "</imapservers>").getBytes(StandardCharsets.UTF_8)));
        return configuration;
    }

    @Test
    void sharedPlainSaslMechanismShouldRequireTlsByDefault() throws Exception {
        assertThat(DefaultImapProcessorFactory.sharedPlainSaslMechanism(imapServers("<imapserver/>")).isAvailableOnTransport(false))
            .isFalse();
    }

    @Test
    void sharedPlainSaslMechanismShouldAllowCleartextWhenEveryServerAllowsIt() throws Exception {
        assertThat(DefaultImapProcessorFactory.sharedPlainSaslMechanism(imapServers(
                "<imapserver><plainAuthDisallowed>false</plainAuthDisallowed></imapserver>"
                    + "<imapserver><auth><requireSSL>false</requireSSL></auth></imapserver>"))
                .isAvailableOnTransport(false))
            .isTrue();
    }

    @Test
    void sharedPlainSaslMechanismShouldRequireTlsWhenOneServerRequiresIt() throws Exception {
        assertThat(DefaultImapProcessorFactory.sharedPlainSaslMechanism(imapServers(
                "<imapserver><plainAuthDisallowed>false</plainAuthDisallowed></imapserver>"
                    + "<imapserver><plainAuthDisallowed>true</plainAuthDisallowed></imapserver>"))
                .isAvailableOnTransport(false))
            .isFalse();
    }
}
