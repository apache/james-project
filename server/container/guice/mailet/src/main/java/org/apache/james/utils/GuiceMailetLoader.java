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

package org.apache.james.utils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.mail.MessagingException;

import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

public class GuiceMailetLoader implements MailetLoader {
    private static final PackageName STANDARD_PACKAGE = PackageName.of("org.apache.james.transport.mailets.");
    private static final NamingScheme MAILET_NAMING_SCHEME = new NamingScheme.OptionalPackagePrefix(STANDARD_PACKAGE);

    private final GuiceGenericLoader genericLoader;
    private final Map<Class<? extends Mailet>, MailetConfig> configurationOverrides;

    @Inject
    public GuiceMailetLoader(GuiceGenericLoader genericLoader, Set<MailetConfigurationOverride> mailetConfigurationOverrides) {
        this.genericLoader = genericLoader;
        this.configurationOverrides = mailetConfigurationOverrides.stream()
            .collect(ImmutableMap.toImmutableMap(
                MailetConfigurationOverride::getClazz,
                MailetConfigurationOverride::getNewConfiguration));
    }

    @Override
    public Mailet getMailet(MailetConfig config) throws MessagingException {
        try {
            ClassName className = new ClassName(config.getMailetName());
            Mailet result = genericLoader.<Mailet>withNamingSheme(MAILET_NAMING_SCHEME)
                .instantiate(className);
            result.init(resolveConfiguration(result, config));
            return result;
        } catch (Exception e) {
            throw new MessagingException("Can not load mailet " + config.getMailetName(), e);
        }
    }

    private MailetConfig resolveConfiguration(Mailet result, MailetConfig providedConfiguration) {
        return Optional.ofNullable(configurationOverrides.get(result.getClass()))
            .orElse(providedConfiguration);
    }
}
