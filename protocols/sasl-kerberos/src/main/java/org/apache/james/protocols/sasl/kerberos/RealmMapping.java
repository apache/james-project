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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;

import com.google.common.collect.ImmutableMap;

public record RealmMapping(Map<String, Domain> domainByRealm) {
    public static final RealmMapping REALM_AS_DOMAIN = new RealmMapping(Map.of());

    private static final String CONFIGURATION_PATH = "realmMapping";

    static RealmMapping from(HierarchicalConfiguration<ImmutableNode> gssapiConfiguration) throws ConfigurationException {
        if (gssapiConfiguration.immutableConfigurationsAt(CONFIGURATION_PATH).isEmpty()) {
            return REALM_AS_DOMAIN;
        }

        List<HierarchicalConfiguration<ImmutableNode>> realms = gssapiConfiguration.configurationsAt(CONFIGURATION_PATH + ".realm");
        if (realms.isEmpty()) {
            throw new ConfigurationException("auth.gssapi.realmMapping must declare at least one realm");
        }

        Map<String, Domain> domainByRealm = new LinkedHashMap<>();
        for (HierarchicalConfiguration<ImmutableNode> realm : realms) {
            String name = name(realm);
            if (domainByRealm.put(name, domain(realm, name)) != null) {
                throw new ConfigurationException("auth.gssapi.realmMapping declares realm " + name + " twice");
            }
        }

        try {
            return new RealmMapping(domainByRealm);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("auth.gssapi.realmMapping is invalid", e);
        }
    }

    private static String name(HierarchicalConfiguration<ImmutableNode> realm) throws ConfigurationException {
        String name = realm.getString("[@name]");
        if (name == null || name.isBlank()) {
            throw new ConfigurationException("auth.gssapi.realmMapping.realm must carry a name attribute");
        }
        return name.trim();
    }

    private static Domain domain(HierarchicalConfiguration<ImmutableNode> realm, String name) throws ConfigurationException {
        String domain = realm.getString("[@domain]");
        if (domain == null || domain.isBlank()) {
            throw new ConfigurationException("auth.gssapi.realmMapping.realm " + name + " must carry a domain attribute");
        }
        try {
            return Domain.of(domain.trim());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("auth.gssapi.realmMapping.realm " + name + " carries an invalid domain", e);
        }
    }

    public RealmMapping {
        Map<Domain, String> realmByDomain = new HashMap<>();
        domainByRealm.forEach((realm, domain) -> {
            if (!realm.equals(realm.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Kerberos realm " + realm + " must be upper case");
            }
            String conflictingRealm = realmByDomain.put(domain, realm);
            if (conflictingRealm != null) {
                throw new IllegalArgumentException("Realms " + conflictingRealm + " and " + realm
                    + " are both mapped onto domain " + domain.asString());
            }
        });
        domainByRealm = ImmutableMap.copyOf(domainByRealm);
    }

    Optional<Username> resolve(CanonicalKerberosPrincipal principal) {
        return domain(principal.realm())
            .map(domain -> Username.of(principal.components() + "@" + domain.asString()));
    }

    private Optional<Domain> domain(String realm) {
        if (domainByRealm.isEmpty()) {
            Domain domain = Domain.of(realm);
            // Case folding is intentional; reject any additional normalization that could collapse distinct realms.
            if (!domain.asString().equals(realm.toLowerCase(Locale.US))) {
                throw new IllegalArgumentException("Kerberos realm " + realm + " cannot be used as a mail domain without normalization");
            }
            return Optional.of(domain);
        }
        return Optional.ofNullable(domainByRealm.get(realm));
    }
}
