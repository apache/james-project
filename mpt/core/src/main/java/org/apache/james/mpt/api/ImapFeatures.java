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
package org.apache.james.mpt.api;

import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class ImapFeatures {
    
    public enum Feature {
        NAMESPACE_SUPPORT,
        MOVE_SUPPORT,
        USER_FLAGS_SUPPORT,
        QUOTA_SUPPORT,
        ANNOTATION_SUPPORT,
        MOD_SEQ_SEARCH
    }

    public static ImapFeatures of(Feature... features) {
        return new ImapFeatures(ImmutableSet.copyOf(features));
    }

    private final ImmutableSet<Feature> supportedFeatures;
    
    private ImapFeatures(ImmutableSet<Feature> supportedFeatures) {
        this.supportedFeatures = supportedFeatures;
    }
    
    public Set<Feature> supportedFeatures() {
        return supportedFeatures;
    }
    
    public boolean supports(Feature... features) {
        Preconditions.checkNotNull(features);
        return supportedFeatures.containsAll(ImmutableSet.copyOf(features));
    }
    
}
