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
package org.apache.james.modules.mailbox;

import java.util.List;
import java.util.Objects;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class PreDeletionHooksConfiguration {
    static final String CONFIGURATION_ENTRY_NAME = "preDeletionHook";

    public static PreDeletionHooksConfiguration from(HierarchicalConfiguration configuration) throws ConfigurationException {
        return new PreDeletionHooksConfiguration(
                configuration.configurationsAt(CONFIGURATION_ENTRY_NAME)
                    .stream()
                    .map(Throwing.function(PreDeletionHookConfiguration::from).sneakyThrow())
                    .collect(Guavate.toImmutableList()));
    }

    public static PreDeletionHooksConfiguration forHooks(PreDeletionHookConfiguration... hooks) {
        return new PreDeletionHooksConfiguration(ImmutableList.copyOf(hooks));
    }

    public static PreDeletionHooksConfiguration none() {
        return new PreDeletionHooksConfiguration(ImmutableList.of());
    }

    private final List<PreDeletionHookConfiguration> hooksConfiguration;

    @VisibleForTesting
    PreDeletionHooksConfiguration(List<PreDeletionHookConfiguration> hooksConfiguration) {
        this.hooksConfiguration = hooksConfiguration;
    }

    public List<PreDeletionHookConfiguration> getHooksConfiguration() {
        return hooksConfiguration;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PreDeletionHooksConfiguration) {
            PreDeletionHooksConfiguration that = (PreDeletionHooksConfiguration) o;

            return Objects.equals(this.hooksConfiguration, that.hooksConfiguration);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(hooksConfiguration);
    }
}
