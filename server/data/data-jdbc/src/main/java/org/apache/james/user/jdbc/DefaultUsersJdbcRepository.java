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

import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.model.DefaultUser;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A Jdbc-backed UserRepository which handles User instances of the
 * <code>DefaultUser</code> class.<br>
 * Although this repository can handle subclasses of DefaultUser, like
 * <code>DefaultJamesUser</code>, only properties from the DefaultUser class are
 * persisted.
 * <p/>
 * TODO Please note that default configuration uses JamesUsersJdbcRepository
 * instead of this class. So we could also delete this implementation.
 */
@Deprecated
public class DefaultUsersJdbcRepository extends AbstractJdbcUsersRepository {

    @Override
    protected User readUserFromResultSet(ResultSet rsUsers) throws SQLException {
        // Get the username, and build a DefaultUser with it.
        String username = rsUsers.getString(1);
        String passwordHash = rsUsers.getString(2);
        String passwordAlg = rsUsers.getString(3);
        DefaultUser user = new DefaultUser(username, passwordHash, passwordAlg);
        return user;
    }

    @Override
    protected void setUserForInsertStatement(User user, PreparedStatement userInsert) throws SQLException {
        DefaultUser defUser = (DefaultUser) user;
        userInsert.setString(1, defUser.getUserName());
        userInsert.setString(2, defUser.getHashedPassword());
        userInsert.setString(3, defUser.getHashAlgorithm());
    }

    @Override
    protected void setUserForUpdateStatement(User user, PreparedStatement userUpdate) throws SQLException {
        DefaultUser defUser = (DefaultUser) user;
        userUpdate.setString(1, defUser.getHashedPassword());
        userUpdate.setString(2, defUser.getHashAlgorithm());
        userUpdate.setString(3, defUser.getUserName());
    }

    @Override
    public void addUser(String username, String password) throws UsersRepositoryException {
        if (contains(username)) {
            throw new UsersRepositoryException("User " + username + " already exist");
        }
        isValidUsername(username);
        User newbie = new DefaultUser(username, "SHA");
        newbie.setPassword(password);
        doAddUser(newbie);
    }

}
