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

import java.util.Optional;

import org.apache.commons.configuration.HierarchicalConfiguration;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class ListenerConfiguration {

    public static ListenerConfiguration from(HierarchicalConfiguration configuration) {
        String listenerClass = configuration.getString("class");
        Preconditions.checkState(!Strings.isNullOrEmpty(listenerClass), "class name is mandatory");
        Optional<Boolean> isAsync = Optional.ofNullable(configuration.getBoolean("async", null));
        Optional<String> group = Optional.ofNullable(configuration.getString("group", null));
        return new ListenerConfiguration(listenerClass, group, extractSubconfiguration(configuration), isAsync);
    }

    public static ListenerConfiguration forClass(String clazz) {
        return new ListenerConfiguration(clazz, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Optional<HierarchicalConfiguration> extractSubconfiguration(HierarchicalConfiguration configuration) {
        return configuration.configurationsAt("configuration")
            .stream()
            .findFirst();
    }

    private final String clazz;
    private final Optional<String> group;
    private final Optional<HierarchicalConfiguration> configuration;
    private final Optional<Boolean> isAsync;

    private ListenerConfiguration(String clazz, Optional<String> group, Optional<HierarchicalConfiguration> configuration, Optional<Boolean> isAsync) {
        this.clazz = clazz;
        this.group = group;
        this.configuration = configuration;
        this.isAsync = isAsync;
    }

    public Optional<String> getGroup() {
        return group;
    }

    public String getClazz() {
        return clazz;
    }

    public Optional<HierarchicalConfiguration> getConfiguration() {
        return configuration;
    }

    public Optional<Boolean> isAsync() {
        return isAsync;
    }
}