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

import java.util.Set;

import org.apache.james.lifecycle.api.Startable;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class Startables {

    private final Set<Class<? extends Startable>> startables;

    public Startables() {
        this.startables = Sets.newLinkedHashSet();
    }

    public void add(Class<? extends Startable> configurable) {
        startables.add(configurable);
    }

    public Set<Class<? extends Startable>> get() {
        return ImmutableSet.copyOf(startables);
    }
}
