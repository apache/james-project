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
import java.util.Locale;

import javax.mail.MessagingException;

import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Matches recipients having the mail sender in the recipient's private
 * whitelist .
 * </p>
 * <p>
 * The recipient name is always converted to its primary name (handling
 * aliases).
 * </p>
 * <p>
 * Configuration string: The database name containing the white list table.
 * </p>
 * <p>
 * Example:
 * 
 * <pre>
 *  &lt;mailet match=&quot;IsInWhiteList=db://maildb&quot; class=&quot;ToProcessor&quot;&gt;
 *     &lt;processor&gt; transport &lt;/processor&gt;
 *  &lt;/mailet&gt;
 * </pre>
 * 
 * </p>
 * 
 * @see org.apache.james.transport.mailets.WhiteListManager
 * @since 2.3.0
 */
@Experimental
public class IsInWhiteList extends AbstractSQLWhitelistMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(IsInWhiteList.class);

    private String selectByPK;

    @Override
    public void init() throws javax.mail.MessagingException {
        super.init();
        selectByPK = sqlQueries.getSqlString("selectByPK", true);
    }

    /**
     * @see org.apache.james.transport.matchers.AbstractSQLWhitelistMatcher#getSQLSectionName()
     */
    protected String getSQLSectionName() {
        return "WhiteList";
    }

    /**
     * @see org.apache.james.transport.matchers.AbstractSQLWhitelistMatcher
     * #matchedWhitelist(org.apache.mailet.MailAddress, org.apache.mailet.Mail)
     */
    protected boolean matchedWhitelist(MailAddress recipientMailAddress, Mail mail) throws MessagingException {
        MailAddress senderMailAddress = mail.getSender();
        String senderUser = senderMailAddress.getLocalPart().toLowerCase(Locale.US);
        String senderHost = senderMailAddress.getDomain().toLowerCase(Locale.US);

        Connection conn = null;
        PreparedStatement selectStmt = null;
        ResultSet selectRS = null;
        try {
            String recipientUser = recipientMailAddress.getLocalPart().toLowerCase(Locale.US);
            String recipientHost = recipientMailAddress.getDomain().toLowerCase(Locale.US);

            if (conn == null) {
                conn = datasource.getConnection();
            }

            try {
                if (selectStmt == null) {
                    selectStmt = conn.prepareStatement(selectByPK);
                }
                selectStmt.setString(1, recipientUser);
                selectStmt.setString(2, recipientHost);
                selectStmt.setString(3, senderUser);
                selectStmt.setString(4, senderHost);
                selectRS = selectStmt.executeQuery();
                if (selectRS.next()) {
                    // This address was already in the list
                    return true;
                }
            } finally {
                jdbcUtil.closeJDBCResultSet(selectRS);
                jdbcUtil.closeJDBCStatement(selectStmt);
            }
            
            try {
                // check for wildcard domain entries
                selectStmt = conn.prepareStatement(selectByPK);
    
                selectStmt.setString(1, recipientUser);
                selectStmt.setString(2, recipientHost);
                selectStmt.setString(3, "*");
                selectStmt.setString(4, senderHost);
                selectRS = selectStmt.executeQuery();
                if (selectRS.next()) {
                    // This address was already in the list
                    return true;
                }
            } finally {
                jdbcUtil.closeJDBCResultSet(selectRS);
                jdbcUtil.closeJDBCStatement(selectStmt);
            }

            try {
                // check for wildcard recipient domain entries
                selectStmt = conn.prepareStatement(selectByPK);
    
                selectStmt.setString(1, "*");
                selectStmt.setString(2, recipientHost);
                selectStmt.setString(3, senderUser);
                selectStmt.setString(4, senderHost);
                selectRS = selectStmt.executeQuery();
                if (selectRS.next()) {
                    // This address was already in the list
                    return true;
                }
            } finally {
                jdbcUtil.closeJDBCResultSet(selectRS);
                jdbcUtil.closeJDBCStatement(selectStmt);
            }

            try {
                    // check for wildcard domain entries on both
                selectStmt = conn.prepareStatement(selectByPK);
    
                selectStmt.setString(1, "*");
                selectStmt.setString(2, recipientHost);
                selectStmt.setString(3, "*");
                selectStmt.setString(4, senderHost);
                selectRS = selectStmt.executeQuery();
                if (selectRS.next()) {
                    // This address was already in the list
                    return true;
                }
            } finally {
                jdbcUtil.closeJDBCResultSet(selectRS);
                jdbcUtil.closeJDBCStatement(selectStmt);
            }
        } catch (SQLException sqle) {
            LOGGER.error("Error accessing database", sqle);
            throw new MessagingException("Exception thrown", sqle);
        } finally {
            theJDBCUtil.closeJDBCConnection(conn);
        }
        return false;
    }

    /**
     * @see org.apache.james.transport.matchers.AbstractSQLWhitelistMatcher
     * #getTableCreateQueryName()
     */
    protected String getTableCreateQueryName() {
        return "createWhiteListTable";
    }

    /**
     * @see
     * org.apache.james.transport.matchers.AbstractSQLWhitelistMatcher#getTableName()
     */
    protected String getTableName() {
        return "whiteListTableName";
    }

}
