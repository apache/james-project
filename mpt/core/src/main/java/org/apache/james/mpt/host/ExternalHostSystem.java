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

package org.apache.james.mpt.host;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.api.Monitor;
import org.apache.james.mpt.api.UserAdder;
import org.apache.james.mpt.session.ExternalSessionFactory;
import org.apache.james.util.Port;

/**
 * <p>
 * Connects to a host system serving on an open port.
 * </p>
 * <p>
 * This is typically used for functional integration testing of a complete
 * server system (including sockets). Apache James MPT AntLib provides an <a
 * href='http://ant.apache.org'>Ant</a> task suitable for this use
 * case.
 * </p>
 */
public class ExternalHostSystem extends ExternalSessionFactory implements ImapHostSystem {

    private final UserAdder userAdder;
    private final ImapFeatures features;

    /**
     * Constructs a host system suitable for connection to an open port.
     * 
     * @param features
     *            set of features supported by the system
     * @param host
     *            host name that will be connected to, not null
     * @param port
     *            port on host that will be connected to, not null
     * @param monitor
     *            monitors the conduct of the connection
     * @param shabang
     *            protocol shabang will be sent to the script test in the place
     *            of the first line received from the server. Many protocols
     *            pass server specific information in the first line. When not
     *            null, this line will be replaced. Or null when the first line
     *            should be passed without replacement
     * @param userAdder
     *            null when test system has appropriate users already set
     */
    public ExternalHostSystem(ImapFeatures features, String host, Port port,
            Monitor monitor, String shabang, UserAdder userAdder) {
        super(host, port, monitor, shabang);
        this.features = features;
        this.userAdder = userAdder;
    }
    
    public ExternalHostSystem(ImapFeatures features, Monitor monitor, String shabang, UserAdder userAdder) {
        super(monitor, shabang);
        this.features = features;
        this.userAdder = userAdder;
    }
    
    @Override
    public boolean addUser(Username user, String password) throws Exception {
        if (userAdder == null) {
            monitor.note("Please ensure user '" + user + "' with password '" + password + "' exists.");
            return false;
        } else {
            userAdder.addUser(user, password);
        }
        return true;
    }

    @Override
    public void createMailbox(MailboxPath mailboxPath) throws Exception {
        throw new NotImplementedException("Not implemented");
    }
    
    public void beforeTests() throws Exception {
    }

    public void afterTests() throws Exception {
    }

    @Override
    public void beforeTest() throws Exception {
    }
    
    @Override
    public void afterTest() throws Exception {
    }

    @Override
    public boolean supports(Feature... features) {
        return this.features.supports(features);
    }

    @Override
    public void setQuotaLimits(QuotaCountLimit maxMessageQuota, QuotaSizeLimit maxStorageQuota) throws Exception {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public void grantRights(MailboxPath mailboxPath, Username userName, MailboxACL.Rfc4314Rights rights) throws Exception {
        throw new NotImplementedException("Not implemented");
    }
}
