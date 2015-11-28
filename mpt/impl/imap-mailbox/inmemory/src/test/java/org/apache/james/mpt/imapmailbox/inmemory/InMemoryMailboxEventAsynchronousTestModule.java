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
package org.apache.james.mpt.imapmailbox.inmemory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.james.mpt.api.HostSystem;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.inmemory.host.InMemoryEventAsynchronousHostSystem;
import org.apache.james.mpt.imapmailbox.inmemory.host.InMemoryHostSystem;

public class InMemoryMailboxEventAsynchronousTestModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(HostSystem.class).to(ImapHostSystem.class);
    }

    @Provides
    @Singleton
    public ImapHostSystem provideHostSystem() throws Exception {
        return InMemoryEventAsynchronousHostSystem.build();
    }

}
