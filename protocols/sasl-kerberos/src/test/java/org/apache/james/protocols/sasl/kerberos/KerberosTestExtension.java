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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.common.collect.ImmutableMap;

/**
 * Runs an embedded Apache Kerby KDC and provisions principals and keytabs for Kerberos tests.
 */
public class KerberosTestExtension implements BeforeAllCallback, AfterAllCallback {
    public static final String SERVER_NAME = "localhost";

    public static String principalProperty(String serviceName) {
        return "james.test.kerberos." + serviceName + ".principal";
    }

    public static String keyTabProperty(String serviceName) {
        return "james.test.kerberos." + serviceName + ".keytab";
    }

    private static boolean isNested(ExtensionContext context) {
        return context.getTestClass()
            .map(testClass -> testClass.isAnnotationPresent(Nested.class))
            .orElse(false);
    }

    private final String[] serviceNames;
    private final Map<String, String> previousProperties;
    private KerberosTestFixture fixture;
    private Map<String, KerberosTestFixture.Service> services;

    public KerberosTestExtension(String... serviceNames) {
        this.serviceNames = Arrays.copyOf(serviceNames, serviceNames.length);
        this.previousProperties = new HashMap<>();
        this.services = ImmutableMap.of();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (isNested(context)) {
            return;
        }

        Path workDirectory = Files.createTempDirectory("james-kerberos-");
        fixture = new KerberosTestFixture(workDirectory);

        try {
            ImmutableMap.Builder<String, KerberosTestFixture.Service> provisionedServices = ImmutableMap.builder();
            for (String serviceName : serviceNames) {
                KerberosTestFixture.Service service = fixture.provisionService(serviceName, SERVER_NAME);
                provisionedServices.put(serviceName, service);
                setProperty(principalProperty(serviceName), service.principal());
                setProperty(keyTabProperty(serviceName), service.keyTab().toString());
            }
            services = provisionedServices.build();
        } catch (Exception e) {
            afterAll(context);
            throw e;
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (isNested(context)) {
            return;
        }

        try {
            if (fixture != null) {
                KerberosTestFixture fixtureToClose = fixture;
                fixture = null;
                fixtureToClose.close();
            }
        } finally {
            previousProperties.forEach((property, previousValue) -> {
                if (previousValue == null) {
                    System.clearProperty(property);
                } else {
                    System.setProperty(property, previousValue);
                }
            });
            previousProperties.clear();
        }
    }

    public KerberosTestFixture.Service service(String serviceName) {
        KerberosTestFixture.Service service = services.get(serviceName);
        if (service == null) {
            throw new IllegalArgumentException("Kerberos service was not provisioned: " + serviceName);
        }
        return service;
    }

    public GssapiTestClient client(String serviceName) throws Exception {
        return fixture.client(service(serviceName));
    }

    public GssapiTestClient client(String serviceName, Optional<String> authorizationId) throws Exception {
        return fixture.client(service(serviceName), authorizationId);
    }

    private void setProperty(String property, String value) {
        if (!previousProperties.containsKey(property)) {
            previousProperties.put(property, System.getProperty(property));
        }
        System.setProperty(property, value);
    }
}
