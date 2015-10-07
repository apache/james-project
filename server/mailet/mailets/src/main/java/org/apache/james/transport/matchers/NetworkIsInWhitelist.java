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
package org.apache.james.transport.matchers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.library.netmatcher.NetMatcher;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

/**
 * <p>
 * Matcher which lookup whitelisted networks in a database. The networks can be
 * specified via netmask.
 * </p>
 * <p>
 * For example: <code>192.168.0.0/24</code>
 * </p>
 * <p>
 * Th whitelisting is done per recipient
 * </p>
 */
public class NetworkIsInWhitelist extends AbstractSQLWhitelistMatcher {

    private DNSService dns;
    private String selectNetworks;

    /**
     * Injection setter for the DNSService.
     * 
     * @param dns
     */
    @Inject
    public void setDNSService(DNSService dns) {
        this.dns = dns;
    }

    /**
     * @see org.apache.james.transport.matchers.AbstractSQLWhitelistMatcher#getSQLSectionName()
     */
    protected String getSQLSectionName() {
        return "NetworkWhiteList";
    }

    @Override
    public void init() throws MessagingException {
        super.init();
        selectNetworks = sqlQueries.getSqlString("selectNetwork", true);

    }

    /**
     * @see org.apache.james.transport.matchers.AbstractSQLWhitelistMatcher
     * #matchedWhitelist(org.apache.mailet.MailAddress, org.apache.mailet.Mail)
     */
    protected boolean matchedWhitelist(MailAddress recipientMailAddress, Mail mail) throws MessagingException {
        Connection conn = null;
        PreparedStatement selectStmt = null;
        ResultSet selectRS = null;
        try {
            String recipientUser = recipientMailAddress.getLocalPart().toLowerCase(Locale.US);
            String recipientHost = recipientMailAddress.getDomain().toLowerCase(Locale.US);

            if (conn == null) {
                conn = datasource.getConnection();
            }

            if (selectStmt == null) {
                selectStmt = conn.prepareStatement(selectNetworks);
            }
            selectStmt.setString(1, recipientUser);
            selectStmt.setString(2, recipientHost);
            selectRS = selectStmt.executeQuery();
            List<String> nets = new ArrayList<String>();
            while (selectRS.next()) {
                nets.add(selectRS.getString(1));
            }
            NetMatcher matcher = new NetMatcher(nets, dns);
            boolean matched = matcher.matchInetNetwork(mail.getRemoteAddr());

            if (!matched) {
                selectStmt = conn.prepareStatement(selectNetworks);
                selectStmt.setString(1, "*");
                selectStmt.setString(2, recipientHost);
                selectRS = selectStmt.executeQuery();
                nets = new ArrayList<String>();
                while (selectRS.next()) {
                    nets.add(selectRS.getString(1));
                }
                matcher = new NetMatcher(nets, dns);
                matched = matcher.matchInetNetwork(mail.getRemoteAddr());
            }
            return matched;
        } catch (SQLException sqle) {
            log("Error accessing database", sqle);
            throw new MessagingException("Exception thrown", sqle);
        } finally {
            theJDBCUtil.closeJDBCResultSet(selectRS);
            theJDBCUtil.closeJDBCStatement(selectStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * @see org.apache.james.transport.matchers.AbstractSQLWhitelistMatcher#getTableCreateQueryName()
     */
    protected String getTableCreateQueryName() {
        return "createNetworkWhiteListTable";
    }

    /**
     * @see
     * org.apache.james.transport.matchers.AbstractSQLWhitelistMatcher#getTableName()
     */
    protected String getTableName() {
        return "networkWhiteListTableName";
    }

}
