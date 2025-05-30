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

package org.apache.james.modules.protocols;

import java.util.Arrays;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.imap.api.ConnectionCheck;
import org.apache.james.imap.api.ConnectionCheckFactory;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.GuiceLoader;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableSet;

public class ConnectionCheckFactoryImpl implements ConnectionCheckFactory {
    private final GuiceLoader guiceLoader;

    @Inject
    public ConnectionCheckFactoryImpl(GuiceLoader guiceLoader) {
        this.guiceLoader = guiceLoader;
    }

    @Override
    public Set<ConnectionCheck> create(HierarchicalConfiguration<ImmutableNode> config) {
        return Arrays.stream(config.getStringArray("additionalConnectionChecks"))
            .collect(ImmutableSet.toImmutableSet())
            .stream()
            .map(ClassName::new)
            .map(Throwing.function(guiceLoader::instantiate))
            .map(ConnectionCheck.class::cast)
            .collect(ImmutableSet.toImmutableSet());
    }
}
