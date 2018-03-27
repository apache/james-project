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
import java.util.Map;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.sql.DataSource;

import org.apache.james.core.MailAddress;
import org.apache.james.util.sql.JDBCUtil;
import org.apache.mailet.Experimental;
import org.apache.mailet.MailetException;

/**
 * <p>
 * Implements a Virtual User Table for JAMES. Derived from the JDBCAlias mailet,
 * but whereas that mailet uses a simple map from a source address to a
 * destination address, this handles simple wildcard selection, verifies that a
 * catchall address is for a domain in the Virtual User Table, and handles
 * forwarding.
 * </p>
 * <p>
 * JDBCRecipientRewriteTable does not provide any administation tools. You'll have to
 * create the RecipientRewriteTable yourself. The standard configuration is as
 * follows:
 * </p>
 * <p>
 * 
 * <pre>
 * CREATE TABLE RecipientRewriteTable
 * (
 *  user varchar(64) NOT NULL default '',
 *  domain varchar(255) NOT NULL default '',
 *  target_address varchar(255) NOT NULL default '',
 *  PRIMARY KEY (user,domain)
 * );
 * </pre>
 * 
 * </p>
 * <p>
 * The user column specifies the username of the virtual recipient, the domain
 * column the domain of the virtual recipient, and the target_address column the
 * email address of the real recipient. The target_address column can contain
 * just the username in the case of a local user, and multiple recipients can be
 * specified in a list separated by commas, semi-colons or colons.
 * </p>
 * <p>
 * The standard query used with RecipientRewriteTable is:
 * 
 * <pre>
 * select RecipientRewriteTable.target_address from RecipientRewriteTable, RecipientRewriteTable as VUTDomains
 * where (RecipientRewriteTable.user like ? or RecipientRewriteTable.user like "\%")
 * and (RecipientRewriteTable.domain like ?
 * or (RecipientRewriteTable.domain like "\%" and VUTDomains.domain like ?))
 * order by concat(RecipientRewriteTable.user,'@',RecipientRewriteTable.domain) desc limit 1
 * </pre>
 * 
 * </p>
 * <p>
 * For a given [user, domain, domain] used with the query, this will match as
 * follows (in precedence order):
 * <ol>
 * <li>user@domain - explicit mapping for user@domain</li>
 * <li>user@% - catchall mapping for user anywhere</li>
 * <li>%@domain - catchall mapping for anyone at domain</li>
 * <li>null - no valid mapping</li>
 * </ol>
 * </p>
 * <p>
 * You need to set the connection. At the moment, there is a limit to what you
 * can change regarding the SQL Query, because there isn't a means to specify
 * where in the query to replace parameters. [TODO]
 * 
 * <pre>
 * &lt;mailet match="All" class="JDBCRecipientRewriteTable"&gt;
 *   &lt;table&gt;db://maildb/RecipientRewriteTable&lt;/table&gt;
 *   &lt;sqlquery&gt;sqlquery&lt;/sqlquery&gt;
 * &lt;/mailet&gt;
 * </pre>
 * 
 * </p>
 * 
 * @deprecated use the definitions in virtualusertable-store.xml instead
 */
@Experimental
@Deprecated
public class JDBCRecipientRewriteTable extends AbstractRecipientRewriteTable {

    // @deprecated QUERY is deprecated - SQL queries are now located in
    // sqlResources.xml
    private static final String QUERY = "select RecipientRewriteTable.target_address from RecipientRewriteTable, RecipientRewriteTable as VUTDomains where (RecipientRewriteTable.user like ? or RecipientRewriteTable.user like '\\%') and (RecipientRewriteTable.domain like ? or (RecipientRewriteTable.domain like '%*%' and VUTDomains.domain like ?)) order by concat(RecipientRewriteTable.user,'@',RecipientRewriteTable.domain) desc limit 1";

    protected DataSource datasource;

    /**
     * The query used by the mailet to get the alias mapping
     */
    protected String query = null;

    /**
     * The JDBCUtil helper class
     */
    private final JDBCUtil theJDBCUtil = new JDBCUtil();

    @Inject
    public void setDataSourceSelector(DataSource datasource) {
        this.datasource = datasource;
    }

    @Override
    public void init() throws MessagingException {
        if (getInitParameter("table") == null) {
            throw new MailetException("Table location not specified for JDBCRecipientRewriteTable");
        }

        String tableURL = getInitParameter("table");

        String datasourceName = tableURL.substring(5);
        int pos = datasourceName.indexOf("/");
        String tableName = datasourceName.substring(pos + 1);
        datasourceName = datasourceName.substring(0, pos);
        Connection conn = null;

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
            query = getInitParameter("sqlquery", QUERY);
        } catch (MailetException me) {
            throw me;
        } catch (Exception e) {
            throw new MessagingException("Error initializing JDBCRecipientRewriteTable", e);
        } finally {
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Map any virtual recipients to real recipients using the configured JDBC
     * connection, table and query.
     * 
     * @param recipientsMap
     *            the mapping of virtual to real recipients
     */
    @Override
    protected void mapRecipients(Map<MailAddress, String> recipientsMap) throws MessagingException {
        Connection conn = null;
        PreparedStatement mappingStmt = null;

        Collection<MailAddress> recipients = recipientsMap.keySet();

        try {
            conn = datasource.getConnection();
            mappingStmt = conn.prepareStatement(query);

            for (MailAddress recipient : recipients) {
                ResultSet mappingRS = null;
                try {
                    mappingStmt.setString(1, recipient.getLocalPart());
                    mappingStmt.setString(2, recipient.getDomain().asString());
                    mappingStmt.setString(3, recipient.getDomain().asString());
                    mappingRS = mappingStmt.executeQuery();
                    if (mappingRS.next()) {
                        String targetString = mappingRS.getString(1);
                        recipientsMap.put(recipient, targetString);
                    }
                } finally {
                    theJDBCUtil.closeJDBCResultSet(mappingRS);
                }
            }
        } catch (SQLException sqle) {
            throw new MessagingException("Error accessing database", sqle);
        } finally {
            theJDBCUtil.closeJDBCStatement(mappingStmt);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    @Override
    public String getMailetInfo() {
        return "JDBC Virtual User Table mailet";
    }
}
