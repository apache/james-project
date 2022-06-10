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

import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration2.Configuration;

import com.google.common.collect.ImmutableList;

public class ExtensionConfiguration {
    public static final ExtensionConfiguration DEFAULT = new ExtensionConfiguration(ImmutableList.of(), ImmutableList.of());

    public static ExtensionConfiguration from(Configuration configuration) {
        return new ExtensionConfiguration(
            Arrays.stream(configuration.getStringArray("guice.extension.module"))
                .map(ClassName::new)
                .collect(ImmutableList.toImmutableList()),
            Arrays.stream(configuration.getStringArray("guice.extension.startable"))
                .map(ClassName::new)
                .collect(ImmutableList.toImmutableList()));
    }

    private final List<ClassName> additionalGuiceModulesForExtensions;
    private final List<ClassName> startables;

    public ExtensionConfiguration(List<ClassName> additionalGuiceModulesForExtensions, List<ClassName> startables) {
        this.additionalGuiceModulesForExtensions = additionalGuiceModulesForExtensions;
        this.startables = startables;
    }

    public List<ClassName> getAdditionalGuiceModulesForExtensions() {
        return additionalGuiceModulesForExtensions;
    }

    public List<ClassName> getStartables() {
        return startables;
    }
}
