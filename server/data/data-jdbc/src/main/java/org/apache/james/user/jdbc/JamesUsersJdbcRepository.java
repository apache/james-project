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

package org.apache.james.user.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.james.core.MailAddress;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.model.DefaultJamesUser;
import org.apache.james.user.lib.model.DefaultUser;

/**
 * A Jdbc-backed UserRepository which handles User instances of the
 * <code>DefaultJamesUser</code> class, or any superclass.
 *
 * @deprecated JAMES-2703 This class is deprecated and will be removed straight after upcoming James 3.4.0 release, unless it finds a maintainer
 *
 * Please migrate to other UsersRepository implementations like JpaUsersRepository
 */
@Deprecated
public class JamesUsersJdbcRepository extends AbstractJdbcUsersRepository {

    @Override
    protected User readUserFromResultSet(ResultSet rsUsers) throws SQLException {
        // Get the column values
        String username = rsUsers.getString(1);
        String pwdHash = rsUsers.getString(2);
        String pwdAlgorithm = rsUsers.getString(3);
        boolean useForwarding = rsUsers.getBoolean(4);
        String forwardingDestination = rsUsers.getString(5);
        boolean useAlias = rsUsers.getBoolean(6);
        String alias = rsUsers.getString(7);

        MailAddress forwardAddress = null;
        if (forwardingDestination != null) {
            try {
                forwardAddress = new MailAddress(forwardingDestination);
            } catch (javax.mail.internet.ParseException pe) {
                String exceptionBuffer = "Invalid mail address in database: " + forwardingDestination + ", for user " + username + ".";
                throw new RuntimeException(exceptionBuffer);
            }
        }

        // Build a DefaultJamesUser with these values, and add to the list.
        DefaultJamesUser user = new DefaultJamesUser(username, pwdHash, pwdAlgorithm);
        user.setForwarding(useForwarding);
        user.setForwardingDestination(forwardAddress);
        user.setAliasing(useAlias);
        user.setAlias(alias);

        return user;
    }

    @Override
    protected void setUserForInsertStatement(User user, PreparedStatement userInsert) throws SQLException {
        setUserForStatement(user, userInsert, false);
    }

    @Override
    protected void setUserForUpdateStatement(User user, PreparedStatement userUpdate) throws SQLException {
        setUserForStatement(user, userUpdate, true);
    }

    /**
     * Sets the data for the prepared statement to match the information in the
     * user object.
     * 
     * @param user
     *            the user whose data is to be stored in the PreparedStatement.
     * @param stmt
     *            the PreparedStatement to be modified.
     * @param userNameLast
     *            whether the user id is the last or the first column
     */
    private void setUserForStatement(User user, PreparedStatement stmt, boolean userNameLast) throws SQLException {
        // Determine column offsets to use, based on username column pos.
        int nameIndex = 1;
        int colOffset = 1;
        if (userNameLast) {
            nameIndex = 7;
            colOffset = 0;
        }

        // Can handle instances of DefaultJamesUser and DefaultUser.
        DefaultJamesUser jamesUser;
        if (user instanceof DefaultJamesUser) {
            jamesUser = (DefaultJamesUser) user;
        } else if (user instanceof DefaultUser) {
            DefaultUser aUser = (DefaultUser) user;
            jamesUser = new DefaultJamesUser(aUser.getUserName(), aUser.getHashedPassword(), aUser.getHashAlgorithm());
        } else {
            // Can't handle any other implementations.
            throw new RuntimeException("An unknown implementation of User was " + "found. This implementation cannot be " + "persisted to a UsersJDBCRepsitory.");
        }

        // Get the user details to save.
        stmt.setString(nameIndex, jamesUser.getUserName());
        stmt.setString(1 + colOffset, jamesUser.getHashedPassword());
        stmt.setString(2 + colOffset, jamesUser.getHashAlgorithm());
        stmt.setInt(3 + colOffset, (jamesUser.getForwarding() ? 1 : 0));

        MailAddress forwardAddress = jamesUser.getForwardingDestination();
        String forwardDestination = null;
        if (forwardAddress != null) {
            forwardDestination = forwardAddress.toString();
        }
        stmt.setString(4 + colOffset, forwardDestination);
        stmt.setInt(5 + colOffset, (jamesUser.getAliasing() ? 1 : 0));
        stmt.setString(6 + colOffset, jamesUser.getAlias());
    }

}
