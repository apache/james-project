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

package org.apache.james.modules;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.james.RunArguments;

import com.google.inject.AbstractModule;

public class RunArgumentsModule extends AbstractModule {
    public static RunArgumentsModule EMPTY = new RunArgumentsModule(new String[0]);

    public final String[] args;

    public RunArgumentsModule(String[] args) {
        this.args = args;
    }

    public RunArgumentsModule mergeArgs(String... mergeArgs) {
       return new RunArgumentsModule(ArrayUtils.addAll(this.args, mergeArgs));
    }

    @Override
    protected void configure() {
        bind(RunArguments.class).toInstance(RunArguments.from(args));
    }
}
