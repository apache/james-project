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

package org.apache.james.modules.data;

import org.apache.james.droplist.lib.DropListManagement;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListManagementMBean;
import org.apache.james.droplists.memory.MemoryDropList;
import org.apache.james.utils.DropListProbeImpl;
import org.apache.james.utils.GuiceProbe;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class MemoryDropListsModule extends AbstractModule {
    @Override
    public void configure() {
        bind(DropList.class).to(MemoryDropList.class).in(Scopes.SINGLETON);
        bind(DropListManagement.class).in(Scopes.SINGLETON);
        bind(DropListManagementMBean.class).to(DropListManagement.class);
        Multibinder.newSetBinder(binder(), GuiceProbe.class)
            .addBinding()
            .to(DropListProbeImpl.class);
    }
}