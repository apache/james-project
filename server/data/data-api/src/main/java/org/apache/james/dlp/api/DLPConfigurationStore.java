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

package org.apache.james.dlp.api;

import java.util.Arrays;
import java.util.Optional;

import org.apache.james.core.Domain;

import com.google.common.collect.ImmutableList;

public interface DLPConfigurationStore extends DLPConfigurationLoader {

    void store(Domain domain, DLPRules rule);

    default void store(Domain domain, DLPConfigurationItem firstRule, DLPConfigurationItem... rules) {
        store(domain, new DLPRules(
            ImmutableList.<DLPConfigurationItem>builder()
                .add(firstRule)
                .addAll(Arrays.asList(rules))
                .build()));
    }

    void clear(Domain domain);

    Optional<DLPConfigurationItem> fetch(Domain domain, DLPConfigurationItem.Id ruleId);

}
