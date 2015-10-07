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

package org.apache.james.transport.mailets;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Vector;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.ParseException;
import javax.sql.DataSource;

import org.apache.james.util.sql.JDBCUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;

/**
 * Rewrites recipient addresses based on a database table. The connection is
 * configured by passing the URL to a conn definition. You need to set the table
 * name to check (or view) along with the source and target columns to use. For
 * example,
 * 
 * <pre>
 * &lt;mailet match="All" class="JDBCAlias"&gt;
 *   &lt;mappings&gt;db://maildb/Aliases&lt;/mappings&gt;
 *   &lt;source_column&gt;source_email_address&lt;/source_column&gt;
 *   &lt;target_column&gt;target_email_address&lt;/target_column&gt;
 * &lt;/mailet&gt;
 * </pre>
 */
public class JDBCAlias extends GenericMailet {

    protected DataSource datasource;
    protected String query = null;

    @Inject
    public void setDataSource(DataSource datasource) {
        this.datasource = datasource;
    }

    // The JDBCUtil helper class
    private final JDBCUtil theJDBCUtil = new JDBCUtil() {
        protected void delegatedLog(String logString) {
            log("JDBCAlias: " + logString);
        }
    };

    /**
     * Initialize the mailet
     */
    public void init() throws MessagingException {
        String mappingsURL = getInitParameter("mappings");

        String datasourceName = mappingsURL.substring(5);
        int pos = datasourceName.indexOf("/");
        String tableName = datasourceName.substring(pos + 1);
        datasourceName = datasourceName.substring(0, pos);

        Connection conn = null;
        if (getInitParameter("source_column") == null) {
            throw new MailetException("source_column not specified for JDBCAlias");
        }
        if (getInitParameter("target_column") == null) {
            throw new MailetException("target_column not specified for JDBCAlias");
        }
        try {

            conn = datasource.getConnection();

            // Check if the required table exists. If not, complain.
            DatabaseMetaData dbMetaData = conn.getMetaData();
            // Need to ask in the case that identifiers are stored, ask the
            // DatabaseMetaInfo.
            // Try UPPER, lower, and MixedCase, to see if the table is there.
            if (!(theJDBCUtil.tableExists(dbMetaData, tableName))) {
                String exceptionBuffer = "Could not find table '" + tableName + "' in datasource '" + datasourceName + "'";
                throw new MailetException(exceptionBuffer);
            }

            // Build the query
            String queryBuffer = "SELECT " + getInitParameter("target_column") + " FROM " + tableName + " WHERE " + getInitParameter("source_column") + " = ?";
            query = queryBuffer;
        } catch (MailetException me) {
            throw me;
        } catch (Exception e) {
            throw new MessagingException("Error initializing JDBCAlias", e);
        } finally {
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    public void service(Mail mail) throws MessagingException {
        // Then loop through each address in the recipient list and try to map
        // it according to the alias table

        Connection conn = null;
        PreparedStatement mappingStmt = null;
        ResultSet mappingRS = null;

        Collection<MailAddress> recipients = mail.getRecipients();
        Collection<MailAddress> recipientsToRemove = new Vector<MailAddress>();
        Collection<MailAddress> recipientsToAdd = new Vector<MailAddress>();
        try {
            conn = datasource.getConnection();
            mappingStmt = conn.prepareStatement(query);

            for (MailAddress recipient : recipients) {
                try {
                    MailAddress source = recipient;
                    mappingStmt.setString(1, source.toString());
                    mappingRS = mappingStmt.executeQuery();
                    if (!mappingRS.next()) {
                        // This address was not found
                        continue;
                    }
                    try {
                        String targetString = mappingRS.getString(1);
                        MailAddress target = new MailAddress(targetString);

                        // Mark this source address as an address to remove from
                        // the recipient list
                        recipientsToRemove.add(source);
                        recipientsToAdd.add(target);
                    } catch (ParseException pe) {
                        // Don't alias this address... there's an invalid
                        // address mapping here
                        String exceptionBuffer = "There is an invalid alias from " + source + " to " + mappingRS.getString(1);
                        log(exceptionBuffer);
                    }
                } finally {
                    ResultSet localRS = mappingRS;
                    // Clear reference to result set
                    mappingRS = null;
                    theJDBCUtil.closeJDBCResultSet(localRS);
                }
            }
        } catch (SQLException sqle) {
            throw new MessagingException("Error accessing database", sqle);
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }

        recipients.removeAll(recipientsToRemove);
        recipients.addAll(recipientsToAdd);
    }

    /**
     * Return a string describing this mailet.
     * 
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "JDBC aliasing mailet";
    }

}
