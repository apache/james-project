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
package org.apache.james.mpt.imapmailbox.external.james.host;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.ExternalHostSystem;
import org.apache.james.mpt.monitor.NullMonitor;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ExternalJamesHostSystem extends ExternalHostSystem {
    
    private static final String ENV_JAMES_ADDRESS = "JAMES_ADDRESS";
    private static final String ENV_JAMES_IMAP_PORT = "JAMES_IMAP_PORT";
    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);
    
    private static final String SHABANG = "* OK IMAP4rev1 Server ready";
    private final Supplier<InetSocketAddress> addressSupplier;

    @Inject
    private ExternalJamesHostSystem(ExternalJamesUserAdder userAdder) {
        super(SUPPORTED_FEATURES, new NullMonitor(), SHABANG, userAdder);
        Preconditions.checkState(System.getenv(ENV_JAMES_ADDRESS) != null, "You must have exported an environment variable called JAMES_ADDRESS in order to run these tests. For instance export JAMES_ADDRESS=127.0.0.1");
        Preconditions.checkState(System.getenv(ENV_JAMES_IMAP_PORT) != null,"You must have exported an environment variable called JAMES_IMAP_PORT in order to run these tests. For instance export JAMES_IMAP_PORT=143");
        this.addressSupplier = () -> new InetSocketAddress(
            System.getenv(ENV_JAMES_ADDRESS),
            Integer.parseInt(System.getenv(ENV_JAMES_IMAP_PORT)));
    }

    @Override
    protected InetSocketAddress getAddress() {
        return addressSupplier.get();
    }
    
    @Override
    public boolean addUser(String user, String password) throws Exception {
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
        throw new NotImplementedException();
    }

    @Override
    public void setQuotaLimits(QuotaCount maxMessageQuota, QuotaSize maxStorageQuota) throws Exception {
        throw new NotImplementedException();
    }
    
}
