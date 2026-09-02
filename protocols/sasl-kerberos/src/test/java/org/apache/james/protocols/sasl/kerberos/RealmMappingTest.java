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

package org.apache.james.protocols.sasl.kerberos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

class RealmMappingTest {
    private static final CanonicalKerberosPrincipal ALICE = new CanonicalKerberosPrincipal("alice", "CORP.EXAMPLE.COM");

    @Test
    void shouldDefaultToRealmAsDomain() throws Exception {
        assertThat(RealmMapping.from(new BaseHierarchicalConfiguration())).isEqualTo(RealmMapping.REALM_AS_DOMAIN);
    }

    @Test
    void realmAsDomainShouldMakeTheRealmTheMailDomain() {
        assertThat(RealmMapping.REALM_AS_DOMAIN.resolve(new CanonicalKerberosPrincipal("alice", "EXAMPLE.COM")))
            .contains(Username.of("alice@example.com"));
    }

    @Test
    void realmAsDomainShouldPreserveMultiComponentPrincipals() {
        assertThat(RealmMapping.REALM_AS_DOMAIN.resolve(new CanonicalKerberosPrincipal("alice/admin", "EXAMPLE.COM")))
            .contains(Username.of("alice/admin@example.com"));
    }

    @Test
    void realmAsDomainShouldRejectRealmsCollapsingOntoAnotherPrincipal() {
        // Domain.of strips the brackets, so this realm would silently collapse onto the EXAMPLE.COM one.
        assertThat(Username.of("alice@[EXAMPLE.COM]")).isEqualTo(Username.of("alice@EXAMPLE.COM"));

        assertThatThrownBy(() -> RealmMapping.REALM_AS_DOMAIN.resolve(new CanonicalKerberosPrincipal("alice", "[EXAMPLE.COM]")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMapRealmOntoConfiguredDomain() throws Exception {
        RealmMapping testee = RealmMapping.from(realmMapping("CORP.EXAMPLE.COM", "example.com"));

        assertThat(testee.resolve(ALICE)).contains(Username.of("alice@example.com"));
    }

    @Test
    void shouldMapEachConfiguredRealm() throws Exception {
        BaseHierarchicalConfiguration configuration = realmMapping("CORP.EXAMPLE.COM", "example.com");
        addRealm(configuration, "OTHER.EXAMPLE.COM", "other.example.com");
        RealmMapping testee = RealmMapping.from(configuration);

        assertThat(testee.resolve(new CanonicalKerberosPrincipal("bob", "OTHER.EXAMPLE.COM")))
            .contains(Username.of("bob@other.example.com"));
    }

    @Test
    void shouldNotResolveUnlistedRealm() throws Exception {
        RealmMapping testee = RealmMapping.from(realmMapping("CORP.EXAMPLE.COM", "example.com"));

        assertThat(testee.resolve(new CanonicalKerberosPrincipal("alice", "EXAMPLE.COM"))).isEmpty();
    }

    @Test
    void shouldPreserveMultiComponentPrincipals() throws Exception {
        RealmMapping testee = RealmMapping.from(realmMapping("CORP.EXAMPLE.COM", "example.com"));

        assertThat(testee.resolve(new CanonicalKerberosPrincipal("alice/admin", "CORP.EXAMPLE.COM")))
            .contains(Username.of("alice/admin@example.com"));
    }

    @Test
    void shouldReadRealmMappingFromXmlConfiguration() throws Exception {
        XMLConfiguration configuration = new XMLConfiguration();
        FileHandler fileHandler = new FileHandler(configuration);
        fileHandler.load(new ByteArrayInputStream("""
            <gssapi>
                <realmMapping>
                    <realm name="CORP.EXAMPLE.COM" domain="example.com"/>
                    <realm name="OTHER.EXAMPLE.COM" domain="other.example.com"/>
                </realmMapping>
            </gssapi>""".getBytes(StandardCharsets.UTF_8)));

        RealmMapping testee = RealmMapping.from(configuration);

        assertThat(testee.resolve(ALICE)).contains(Username.of("alice@example.com"));
        assertThat(testee.resolve(new CanonicalKerberosPrincipal("bob", "OTHER.EXAMPLE.COM")))
            .contains(Username.of("bob@other.example.com"));
    }

    @Test
    void shouldRejectEmptyRealmMapping() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("realmMapping", "");

        assertThatThrownBy(() -> RealmMapping.from(configuration))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("auth.gssapi.realmMapping must declare at least one realm");
    }

    @Test
    void shouldRejectRealmWithoutName() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("realmMapping.realm(-1)[@domain]", "example.com");

        assertThatThrownBy(() -> RealmMapping.from(configuration))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("auth.gssapi.realmMapping.realm must carry a name attribute");
    }

    @Test
    void shouldRejectRealmWithoutDomain() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("realmMapping.realm(-1)[@name]", "CORP.EXAMPLE.COM");

        assertThatThrownBy(() -> RealmMapping.from(configuration))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("auth.gssapi.realmMapping.realm CORP.EXAMPLE.COM must carry a domain attribute");
    }

    @Test
    void shouldRejectInvalidDomain() {
        assertThatThrownBy(() -> RealmMapping.from(realmMapping("CORP.EXAMPLE.COM", "exa mple.com")))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("auth.gssapi.realmMapping.realm CORP.EXAMPLE.COM carries an invalid domain");
    }

    @Test
    void shouldRejectDuplicatedRealm() {
        BaseHierarchicalConfiguration configuration = realmMapping("CORP.EXAMPLE.COM", "example.com");
        addRealm(configuration, "CORP.EXAMPLE.COM", "other.example.com");

        assertThatThrownBy(() -> RealmMapping.from(configuration))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("auth.gssapi.realmMapping declares realm CORP.EXAMPLE.COM twice");
    }

    @Test
    void shouldRejectRealmsSharingADomain() {
        BaseHierarchicalConfiguration configuration = realmMapping("CORP.EXAMPLE.COM", "example.com");
        addRealm(configuration, "OTHER.EXAMPLE.COM", "example.com");

        assertThatThrownBy(() -> RealmMapping.from(configuration))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("auth.gssapi.realmMapping is invalid")
            .hasRootCauseMessage("Realms CORP.EXAMPLE.COM and OTHER.EXAMPLE.COM are both mapped onto domain example.com");
    }

    @Test
    void shouldRejectRealmsSharingADomainSpelledDifferently() {
        BaseHierarchicalConfiguration configuration = realmMapping("CORP.EXAMPLE.COM", "example.com");
        addRealm(configuration, "OTHER.EXAMPLE.COM", "EXAMPLE.COM");

        assertThatThrownBy(() -> RealmMapping.from(configuration))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("auth.gssapi.realmMapping is invalid");
    }

    @Test
    void shouldRejectLowerCaseRealm() {
        assertThatThrownBy(() -> RealmMapping.from(realmMapping("corp.example.com", "example.com")))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("auth.gssapi.realmMapping is invalid")
            .hasRootCauseMessage("Kerberos realm corp.example.com must be upper case");
    }

    @Test
    void constructorShouldRejectRealmsSharingADomain() {
        Map<String, Domain> domainByRealm = Map.of(
            "CORP.EXAMPLE.COM", Domain.of("example.com"),
            "OTHER.EXAMPLE.COM", Domain.of("example.com"));

        assertThatThrownBy(() -> new RealmMapping(domainByRealm))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private BaseHierarchicalConfiguration realmMapping(String realm, String domain) {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        addRealm(configuration, realm, domain);
        return configuration;
    }

    private void addRealm(BaseHierarchicalConfiguration configuration, String realm, String domain) {
        configuration.addProperty("realmMapping.realm(-1)[@name]", realm);
        configuration.addProperty("realmMapping.realm[@domain]", domain);
    }
}
