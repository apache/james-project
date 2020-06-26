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

package org.apache.james.smtp.extensions;

import java.util.ArrayList;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.protocols.smtp.hook.Hook;
import org.junit.rules.ExternalResource;

import com.google.common.collect.ImmutableList;

public class StaticInputChecker extends ExternalResource {
    private static final ArrayList<Pair<Class<? extends Hook>, ?>> results = new ArrayList<>();

    public static void registerHookResult(Class<? extends Hook> clazz, Object result) {
        results.add(Pair.of(clazz, result));
    }

    @Override
    protected void after() {
        results.clear();
    }

    public ImmutableList<Pair<Class<? extends Hook>, ?>> getResults() {
        return ImmutableList.copyOf(results);
    }
}
