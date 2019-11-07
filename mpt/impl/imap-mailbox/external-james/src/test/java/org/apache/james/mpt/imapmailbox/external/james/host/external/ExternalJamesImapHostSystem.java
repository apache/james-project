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
package org.apache.james.mpt.imapmailbox.external.james.host.external;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.ExternalHostSystem;
import org.apache.james.mpt.monitor.NullMonitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ExternalJamesImapHostSystem extends ExternalHostSystem {

    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);
    
    private static final String SHABANG = "* OK IMAP4rev1 Server ready";
    private final Supplier<InetSocketAddress> addressSupplier;

    @Inject
    private ExternalJamesImapHostSystem(NoopDomainsAndUserAdder userAdder, ExternalJamesConfiguration configuration) {
        super(SUPPORTED_FEATURES, new NullMonitor(), SHABANG, userAdder);
        this.addressSupplier = () -> new InetSocketAddress(configuration.getAddress(), configuration.getImapPort().getValue());
    }

    @Override
    protected InetSocketAddress getAddress() {
        return addressSupplier.get();
    }
    
    @Override
    public boolean addUser(Username user, String password) throws Exception {
        return super.addUser(user, password);
    }
    
    @Override
    public void beforeTest() throws Exception {

    }

    @Override
    public void afterTest() throws Exception {

    }

    @Override
    public void createMailbox(MailboxPath mailboxPath) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public void setQuotaLimits(QuotaCountLimit maxMessageQuota, QuotaSizeLimit maxStorageQuota) throws Exception {
        throw new NotImplementedException("Not implemented");
    }
    
}
