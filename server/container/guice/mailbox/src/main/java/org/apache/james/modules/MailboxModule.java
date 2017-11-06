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

import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.utils.GuiceProbe;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class MailboxModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder<GuiceProbe> probeMultiBinder = Multibinder.newSetBinder(binder(), GuiceProbe.class);
        probeMultiBinder.addBinding().to(MailboxProbeImpl.class);
        probeMultiBinder.addBinding().to(QuotaProbesImpl.class);
        probeMultiBinder.addBinding().to(ACLProbeImpl.class);

        bind(UnionMailboxACLResolver.class).in(Scopes.SINGLETON);
        bind(MailboxACLResolver.class).to(UnionMailboxACLResolver.class);
        bind(SimpleGroupMembershipResolver.class).in(Scopes.SINGLETON);
        bind(GroupMembershipResolver.class).to(SimpleGroupMembershipResolver.class);
    }

}
