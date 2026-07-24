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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.security.auth.kerberos.KerberosPrincipal;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;

public record GssapiSaslConfiguration(String serviceName,
                                      String serverName,
                                      String principal,
                                      Path keyTab,
                                      boolean requireSSL) {
    private static final String CONFIGURATION_PATH = "auth.gssapi";
    private static final boolean REQUIRE_SSL_DEFAULT = true;

    public static GssapiSaslConfiguration from(HierarchicalConfiguration<ImmutableNode> serverConfiguration) throws ConfigurationException {
        if (serverConfiguration.immutableConfigurationsAt(CONFIGURATION_PATH).isEmpty()) {
            throw new ConfigurationException("GSSAPI SASL mechanism requires an auth.gssapi configuration");
        }

        HierarchicalConfiguration<ImmutableNode> configuration = serverConfiguration.configurationAt(CONFIGURATION_PATH);
        String serviceName = required(configuration, "serviceName");
        String serverName = required(configuration, "serverName");
        String principal = required(configuration, "principal");
        Path keyTab = keyTab(required(configuration, "keyTab"));
        validatePrincipal(serviceName, serverName, principal);

        return new GssapiSaslConfiguration(serviceName, serverName, principal, keyTab,
            serverConfiguration.getBoolean("auth.requireSSL", REQUIRE_SSL_DEFAULT));
    }

    private static String required(HierarchicalConfiguration<ImmutableNode> configuration, String property) throws ConfigurationException {
        String value = configuration.getString(property);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("auth.gssapi." + property + " must be specified");
        }
        return value.trim();
    }

    private static Path keyTab(String value) throws ConfigurationException {
        try {
            Path path = value.startsWith("file:") ? Path.of(URI.create(value)) : Path.of(value);
            Path normalizedPath = path.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalizedPath) || !Files.isReadable(normalizedPath)) {
                throw new ConfigurationException("auth.gssapi.keyTab must reference a readable regular file");
            }
            return normalizedPath;
        } catch (RuntimeException e) {
            throw new ConfigurationException("auth.gssapi.keyTab is invalid", e);
        }
    }

    private static void validatePrincipal(String serviceName, String serverName, String principal) throws ConfigurationException {
        try {
            KerberosPrincipal kerberosPrincipal = new KerberosPrincipal(principal);
            String expectedPrincipal = serviceName + "/" + serverName + "@" + kerberosPrincipal.getRealm();
            if (!kerberosPrincipal.getName().equals(expectedPrincipal)) {
                throw new ConfigurationException("auth.gssapi.principal must match the configured serviceName and serverName");
            }
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("auth.gssapi.principal is invalid", e);
        }
    }
}
