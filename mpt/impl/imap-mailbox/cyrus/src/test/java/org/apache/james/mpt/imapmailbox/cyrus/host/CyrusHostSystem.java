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
package org.apache.james.mpt.imapmailbox.cyrus.host;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.api.Session;
import org.apache.james.mpt.api.UserAdder;
import org.apache.james.mpt.host.ExternalHostSystem;
import org.apache.james.mpt.monitor.NullMonitor;
import org.apache.james.mpt.protocol.ProtocolSession;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.spotify.docker.client.messages.ContainerCreation;

@Singleton
public class CyrusHostSystem extends ExternalHostSystem implements Provider<ContainerCreation> {

    private static final String CREATE_MAILBOX_LOCATION = "CyrusHostSystem.createMailbox";
    private static final String SHABANG = "* OK IMAP4rev1 Server ready";
    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);

    private final Docker docker;
    private Supplier<InetSocketAddress> addressSupplier;
    private ContainerCreation container;

    @Inject
    private CyrusHostSystem(Docker docker, UserAdder userAdder) {
        super(SUPPORTED_FEATURES, new NullMonitor(), SHABANG, userAdder);
        this.docker = docker;
        
    }

    @Override
    protected InetSocketAddress getAddress() {
        return addressSupplier.get();
    }
    
    @Override
    public boolean addUser(Username user, String password) throws Exception {
        return super.addUser(user, password) && createUserInbox(user);
    }

    private boolean createUserInbox(Username username) {
        createMailbox(MailboxPath.forUser(username, ""));
        return true;
    }
    
    @Override
    public void beforeTest() throws Exception {
        container = docker.start();
        addressSupplier = () -> new InetSocketAddress(docker.getHost(container), docker.getIMAPPort(container));
    }

    @Override
    public void afterTest() throws Exception {
        docker.stop(container);
        container = null;
        addressSupplier = null;
    }
    
    @Override
    public ContainerCreation get() {
        return container;
    }
    
    @Override
    public void createMailbox(MailboxPath mailboxPath) {
        ProtocolSession protocolSession = logAndGetAdminProtocolSession(new ProtocolSession());
        protocolSession.cl(String.format("A1 CREATE %s", createMailboxStringFromMailboxPath(mailboxPath)));
        protocolSession.sl("A1 OK .*", CREATE_MAILBOX_LOCATION);
        executeProtocolSession(logoutAndGetProtocolSession(protocolSession));
    }

    public ProtocolSession logoutAndGetProtocolSession(ProtocolSession protocolSession) {
        protocolSession.cl("A2 LOGOUT");
        protocolSession.sl("\\* BYE .*", CREATE_MAILBOX_LOCATION);
        return protocolSession;
    }

    public ProtocolSession logAndGetAdminProtocolSession(ProtocolSession protocolSession) {
        protocolSession.sl(".*", CREATE_MAILBOX_LOCATION);
        protocolSession.cl(". LOGIN cyrus cyrus");
        protocolSession.sl("\\. OK .*", CREATE_MAILBOX_LOCATION);
        return protocolSession;
    }

    public String createMailboxStringFromMailboxPath(MailboxPath mailboxPath) {
        return Joiner.on('.').skipNulls()
            .join("user",
                mailboxPath.getUser(),
                Strings.emptyToNull(mailboxPath.getName()));
    }

    public void executeProtocolSession(ProtocolSession protocolSession) {
        try {
            Session session = newSession(null);
            try {
                session.start();
                protocolSession.runSessions(new Session[]{session});
            } finally {
                session.stop();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setQuotaLimits(QuotaCountLimit maxMessageQuota, QuotaSizeLimit maxStorageQuota) throws Exception {
        throw new NotImplementedException("not implemented");
    }
}
