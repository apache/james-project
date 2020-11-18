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
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class ListenersConfiguration {

    public static ListenersConfiguration of(ListenerConfiguration... listenersConfiguration) {
        return new ListenersConfiguration(ImmutableList.copyOf(listenersConfiguration), true);
    }

    public static ListenersConfiguration disabled() {
        return new ListenersConfiguration(ImmutableList.of(), false);
    }

    public static ListenersConfiguration from(HierarchicalConfiguration<ImmutableNode> configuration) {
        List<HierarchicalConfiguration<ImmutableNode>> listeners = configuration.configurationsAt("listener");
        Optional<Boolean> consumeGroups = Optional.ofNullable(configuration.getBoolean("executeGroupListeners", null));

        return new ListenersConfiguration(listeners
                .stream()
                .map(ListenerConfiguration::from)
                .collect(Guavate.toImmutableList()),
            consumeGroups.orElse(true));
    }
    
    private final List<ListenerConfiguration> listenersConfiguration;
    private final boolean enableGroupListenerConsumption;

    @VisibleForTesting ListenersConfiguration(List<ListenerConfiguration> listenersConfiguration, boolean enableGroupListenerConsumption) {
        Preconditions.checkArgument(enableGroupListenerConsumption || listenersConfiguration.isEmpty(),
            "'executeGroupListeners' can not be false while extra listeners are configured");
        this.listenersConfiguration = listenersConfiguration;
        this.enableGroupListenerConsumption = enableGroupListenerConsumption;
    }

    public List<ListenerConfiguration> getListenersConfiguration() {
        return listenersConfiguration;
    }

    public boolean isGroupListenerConsumptionEnabled() {
        return enableGroupListenerConsumption;
    }
}
