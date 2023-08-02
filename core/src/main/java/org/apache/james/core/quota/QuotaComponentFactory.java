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

package org.apache.james.core.quota;

import java.util.Set;

import com.google.inject.Inject;

public class QuotaComponentFactory {
    private Set<QuotaComponent> set;

    @Inject
    public QuotaComponentFactory(Set<QuotaComponent> set) {
        this.set = set;
    }

    public QuotaComponent parse(String value) {
        for (QuotaComponent quotaComponent : set) {
            if (quotaComponent.asString().equals(value)) {
                return quotaComponent;
            }
        }
        throw new RuntimeException("Could not parse " + value + " to QuotaComponent");
    }
}
