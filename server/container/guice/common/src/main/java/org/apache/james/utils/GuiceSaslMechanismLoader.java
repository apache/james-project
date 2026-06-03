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

import java.util.Collection;

import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismLoader;
import org.apache.james.protocols.api.sasl.SaslMechanismLoadingException;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class GuiceSaslMechanismLoader implements SaslMechanismLoader {
    private final GuiceLoader.InvocationPerformer<SaslMechanism> mechanismLoader;

    @Inject
    public GuiceSaslMechanismLoader(GuiceLoader guiceLoader) {
        this.mechanismLoader = guiceLoader.withNamingSheme(DefaultSaslMechanismNamingScheme.asNamingScheme());
    }

    @Override
    public ImmutableList<SaslMechanism> load(Collection<String> classNames) {
        return classNames.stream()
            .map(this::load)
            .collect(ImmutableList.toImmutableList());
    }

    private SaslMechanism load(String className) {
        try {
            return mechanismLoader.instantiate(new ClassName(className));
        } catch (Exception e) {
            throw new SaslMechanismLoadingException("Can not load SASL mechanism " + className, e);
        }
    }
}
