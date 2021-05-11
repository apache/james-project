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

package org.apache.james.mailets.configuration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath;

import java.io.IOException;

import javax.xml.transform.Source;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;
import org.xmlunit.builder.Input;
import org.xmlunit.xpath.JAXPXPathEngine;
import org.xmlunit.xpath.XPathEngine;

public class SmtpConfigurationTest {
    @Test
    public void authenticationCanBeRequired() throws IOException {
        assertThat(SmtpConfiguration.builder()
                .requireAuthentication()
                .build()
                .serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/authRequired/text()",
                is("true")));
    }

    @Test
    public void maxMessageSizeCanBeCustomized() throws IOException {
        assertThat(SmtpConfiguration.builder()
                .withMaxMessageSizeInKb(36)
                .build()
                .serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/maxmessagesize/text()",
                is("36")));
    }

    @Test
    public void bracketEnforcementCanBeDisable() throws IOException {
        assertThat(SmtpConfiguration.builder()
                .doNotRequireBracketEnforcement()
                .build()
                .serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/addressBracketsEnforcement/text()",
                is("false")));
    }

    @Test
    public void verifyIdentityEnforcementCanBeDisabled() throws IOException {
        assertThat(SmtpConfiguration.builder()
                .doNotVerifyIdentity()
                .build()
                .serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/verifyIdentity/text()",
                is("false")));
    }

    @Test
    public void authenticationCanBeDisabled() throws IOException {
        assertThat(SmtpConfiguration.builder()
                .build()
                .serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/authRequired/text()",
                is("false")));
    }

    @Test
    public void bracketEnforcementCanBeEnabled() throws IOException {
        assertThat(SmtpConfiguration.builder()
                .requireBracketEnforcement()
                .build()
                .serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/addressBracketsEnforcement/text()",
                is("true")));
    }

    @Test
    public void verifyIdentityEnforcementCanBeEnabled() throws IOException {
        assertThat(SmtpConfiguration.builder()
                .verifyIdentity()
                .build()
                .serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/verifyIdentity/text()",
                is("true")));
    }

    @Test
    public void specificNetworkCanBeAuthorized() throws IOException {
        String network = "172.0.0.0/24";
        assertThat(SmtpConfiguration.builder()
                .withAutorizedAddresses(network)
                .build()
                .serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/authorizedAddresses/text()",
                is(network)));
    }

    @Test
    public void defaultSmtpConfigurationShouldNotHaveAuthorizedNetwork() throws IOException {
        String xmlFile = SmtpConfiguration.DEFAULT.serializeAsXml();
        Source source = Input.fromString(xmlFile).build();
        XPathEngine xpath = new JAXPXPathEngine();
        Iterable<Node> allMatches = xpath.selectNodes("/smtpservers/smtpserver/authorizedAddresses", source);

        Assertions.assertThat(allMatches).isEmpty();
    }

    @Test
    public void authenticationShouldNotBeRequiredByDefault() throws IOException {
        assertThat(SmtpConfiguration.DEFAULT.serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/authRequired/text()",
                is("false")));
    }

    @Test
    public void maxMessageSizeShouldBeDisabledByDefault() throws IOException {
        assertThat(SmtpConfiguration.DEFAULT.serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/maxmessagesize/text()",
                is("0")));
    }

    @Test
    public void addressBracketsEnforcementShouldBeEnforcedByDefault() throws IOException {
        assertThat(SmtpConfiguration.DEFAULT.serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/addressBracketsEnforcement/text()",
                is("true")));
    }

    @Test
    public void verifyIdentityShouldBeDisabledByDefault() throws IOException {
        assertThat(SmtpConfiguration.DEFAULT.serializeAsXml(),
            hasXPath("/smtpservers/smtpserver/verifyIdentity/text()",
                is("false")));
    }
}
