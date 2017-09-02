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

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.sql.DataSource;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.AbstractJamesUsersRepository;
import org.apache.james.util.sql.JDBCUtil;
import org.apache.james.util.sql.SqlResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract base class for creating UserRepository implementations which use
 * a database for persistence.
 * 
 * To implement a new UserRepository using by extending this class, you need to
 * implement the 3 abstract methods defined below, and define the required SQL
 * statements in an SQLResources file.
 * 
 * The SQL statements used by this implementation are:
 * <table>
 * <tr>
 * <td><b>Required</b></td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>select</td>
 * <td>Select all users.</td>
 * </tr>
 * <tr>
 * <td>insert</td>
 * <td>Insert a user.</td>
 * </tr>
 * <tr>
 * <td>update</td>
 * <td>Update a user.</td>
 * </tr>
 * <tr>
 * <td>delete</td>
 * <td>Delete a user by name.</td>
 * </tr>
 * <tr>
 * <td>createTable</td>
 * <td>Create the users table.</td>
 * </tr>
 * <tr>
 * <td><b>Optional</b></td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>selectByLowercaseName</td>
 * <td>Select a user by name (case-insensitive lowercase).</td>
 * </tr>
 * </table>
 */
@Deprecated
public abstract class AbstractJdbcUsersRepository extends AbstractJamesUsersRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJdbcUsersRepository.class);

    protected Map<String, String> m_sqlParameters;

    private String m_sqlFileName;

    private DataSource m_datasource;

    // Fetches all Users from the db.
    private String m_getUsersSql;

    // This fetch a user by name, ensuring case-insensitive matching.
    private String m_userByNameCaseInsensitiveSql;

    // Insert, update and delete sql statements are not guaranteed
    // to be case-insensitive; this is handled in code.
    private String m_insertUserSql;

    private String m_updateUserSql;

    private String m_deleteUserSql;

    // The JDBCUtil helper class
    private JDBCUtil theJDBCUtil;

    private FileSystem fileSystem;

    /**
     * Removes a user from the repository
     * 
     * @param userName
     *            the user to be removed
     * @throws UsersRepositoryException
     */
    public void removeUser(String userName) throws UsersRepositoryException {
        User user = getUserByName(userName);
        if (user != null) {
            doRemoveUser(user);
        } else {
            throw new UsersRepositoryException("User " + userName + " does not exist");
        }
    }

    /**
     * Get the user object with the specified user name. Return null if no such
     * user.
     * 
     * @param name
     *            the name of the user to retrieve
     * 
     * @return the user if found, null otherwise
     * 
     * @since James 1.2.2
     */
    public User getUserByName(String name) throws UsersRepositoryException {
        return getUserByName(name, ignoreCase);
    }

    /**
     * Returns whether or not this user is in the repository
     * 
     * @return true or false
     */
    public boolean contains(String name) throws UsersRepositoryException {
        User user = getUserByName(name, ignoreCase);
        return (user != null);
    }

    /**
     * Returns whether or not this user is in the repository. Names are matched
     * on a case insensitive basis.
     * 
     * @return true or false
     */
    public boolean containsCaseInsensitive(String name) throws UsersRepositoryException {
        User user = getUserByName(name, true);
        return (user != null);
    }

    /**
     * Test if user with name 'name' has password 'password'.
     * 
     * @param name
     *            the name of the user to be tested
     * @param password
     *            the password to be tested
     * 
     * @return true if the test is successful, false if the password is
     *         incorrect or the user doesn't exist
     * @since James 1.2.2
     */
    public boolean test(String name, String password) throws UsersRepositoryException {
        User user = getUserByName(name, ignoreCase);
        return user != null && user.verifyPassword(password);
    }

    /**
     * Returns a count of the users in the repository.
     * 
     * @return the number of users in the repository
     */
    public int countUsers() throws UsersRepositoryException {
        List<String> usernames = listUserNames();
        return usernames.size();
    }

    /**
     * List users in repository.
     * 
     * @return Iterator over a collection of Strings, each being one user in the
     *         repository.
     */
    public Iterator<String> list() throws UsersRepositoryException {
        return listUserNames().iterator();
    }

    /**
     * Set the DataSourceSelector
     * 
     * @param m_datasource
     *            the DataSourceSelector
     */
    @Inject
    public void setDatasource(DataSource m_datasource) {
        this.m_datasource = m_datasource;
    }

    /**
     * Sets the filesystem service
     * 
     * @param system
     *            the new service
     */
    @Inject
    public void setFileSystem(FileSystem system) {
        this.fileSystem = system;
    }

    /**
     * Initialises the JDBC repository.
     * <ol>
     * <li>Tests the connection to the database.</li>
     * <li>Loads SQL strings from the SQL definition file, choosing the
     * appropriate SQL for this connection, and performing parameter
     * substitution,</li>
     * <li>Initialises the database with the required tables, if necessary.</li>
     * </ol>
     * 
     * @throws Exception
     *             if an error occurs
     */
    @PostConstruct
    public void init() throws Exception {
        StringBuffer logBuffer;
        if (LOGGER.isDebugEnabled()) {
            logBuffer = new StringBuffer(128).append(this.getClass().getName()).append(".initialize()");
            LOGGER.debug(logBuffer.toString());
        }

        theJDBCUtil = new JDBCUtil();

        // Test the connection to the database, by getting the DatabaseMetaData.
        Connection conn = openConnection();
        try {
            DatabaseMetaData dbMetaData = conn.getMetaData();

            InputStream sqlFile;

            try {
                sqlFile = fileSystem.getResource(m_sqlFileName);
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            }

            if (LOGGER.isDebugEnabled()) {
                logBuffer = new StringBuffer(256).append("Reading SQL resources from: ").append(m_sqlFileName).append(", section ").append(this.getClass().getName()).append(".");
                LOGGER.debug(logBuffer.toString());
            }

            SqlResources sqlStatements = new SqlResources();
            sqlStatements.init(sqlFile, this.getClass().getName(), conn, m_sqlParameters);

            // Create the SQL Strings to use for this table.
            // Fetches all Users from the db.
            m_getUsersSql = sqlStatements.getSqlString("select", true);

            // Get a user by lowercase name. (optional)
            // If not provided, the entire list is iterated to find a user.
            m_userByNameCaseInsensitiveSql = sqlStatements.getSqlString("selectByLowercaseName");

            // Insert, update and delete are not guaranteed to be
            // case-insensitive
            // Will always be called with correct case in username..
            m_insertUserSql = sqlStatements.getSqlString("insert", true);
            m_updateUserSql = sqlStatements.getSqlString("update", true);
            m_deleteUserSql = sqlStatements.getSqlString("delete", true);

            // Creates a single table with "username" the Primary Key.
            String createUserTableSql = sqlStatements.getSqlString("createTable", true);

            // Check if the required table exists. If not, create it.
            // The table name is defined in the SqlResources.
            String tableName = sqlStatements.getSqlString("tableName", true);

            // Need to ask in the case that identifiers are stored, ask the
            // DatabaseMetaInfo.
            // NB this should work, but some drivers (eg mm MySQL)
            // don't return the right details, hence the hackery below.
            /*
             * String tableName = m_tableName; if (
             * dbMetaData.storesLowerCaseIdentifiers() ) { tableName =
             * tableName.toLowerCase(Locale.US); } else if (
             * dbMetaData.storesUpperCaseIdentifiers() ) { tableName =
             * tableName.toUpperCase(Locale.US); }
             */

            // Try UPPER, lower, and MixedCase, to see if the table is there.
            if (!theJDBCUtil.tableExists(dbMetaData, tableName)) {
                // Users table doesn't exist - create it.
                PreparedStatement createStatement = null;
                try {
                    createStatement = conn.prepareStatement(createUserTableSql);
                    createStatement.execute();
                } finally {
                    theJDBCUtil.closeJDBCStatement(createStatement);
                }

                logBuffer = new StringBuffer(128).append(this.getClass().getName()).append(": Created table \'").append(tableName).append("\'.");
                LOGGER.info(logBuffer.toString());
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Using table: " + tableName);
                }
            }

        } finally {
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * <p>
     * Configures the UserRepository for JDBC access.
     * </p>
     * <p>
     * Requires a configuration element in the .conf.xml file of the form:
     * </p>
     * 
     * <pre>
     *   &lt;repository name=&quot;so even &quot;
     *       class=&quot;org.apache.james.userrepository.JamesUsersJdbcRepository&quot;&gt;
     *       &lt;!-- Name of the datasource to use --&gt;
     *       &lt;data-source&gt;MailDb&lt;/data-source&gt;
     *       &lt;!-- File to load the SQL definitions from --&gt;
     *       &lt;sqlFile&gt;dist/conf/sqlResources.xml&lt;/sqlFile&gt;
     *       &lt;!-- replacement parameters for the sql file --&gt;
     *       &lt;sqlParameters table=&quot;JamesUsers&quot;/&gt;
     *   &lt;/repository&gt;
     * </pre>
     * 
     * @see org.apache.james.user.lib.AbstractJamesUsersRepository#doConfigure(org.apache.commons.configuration.HierarchicalConfiguration)
     */
    protected void doConfigure(HierarchicalConfiguration configuration) throws ConfigurationException {
        StringBuffer logBuffer;
        if (LOGGER.isDebugEnabled()) {
            logBuffer = new StringBuffer(64).append(this.getClass().getName()).append(".configure()");
            LOGGER.debug(logBuffer.toString());
        }

        // Parse the DestinationURL for the name of the datasource,
        // the table to use, and the (optional) repository Key.
        String destUrl = configuration.getString("[@destinationURL]", null);
        // throw an exception if the attribute is missing
        if (destUrl == null)
            throw new ConfigurationException("destinationURL attribute is missing from Configuration");

        // normalise the destination, to simplify processing.
        if (!destUrl.endsWith("/")) {
            destUrl += "/";
        }
        // Split on "/", starting after "db://"
        List<String> urlParams = new ArrayList<>();
        int start = 5;
        int end = destUrl.indexOf('/', start);
        while (end > -1) {
            urlParams.add(destUrl.substring(start, end));
            start = end + 1;
            end = destUrl.indexOf('/', start);
        }

        // Build SqlParameters and get datasource name from URL parameters
        m_sqlParameters = new HashMap<>();
        switch (urlParams.size()) {
        case 3:
            m_sqlParameters.put("key", urlParams.get(2));
        case 2:
            m_sqlParameters.put("table", urlParams.get(1));
        case 1:
            urlParams.get(0);
            break;
        default:
            throw new ConfigurationException("Malformed destinationURL - " + "Must be of the format \"db://<data-source>[/<table>[/<key>]]\".");
        }

        if (LOGGER.isDebugEnabled()) {
            logBuffer = new StringBuffer(128).append("Parsed URL: table = '").append(m_sqlParameters.get("table")).append("', key = '").append(m_sqlParameters.get("key")).append("'");
            LOGGER.debug(logBuffer.toString());
        }

        // Get the SQL file location
        m_sqlFileName = configuration.getString("sqlFile", null);

        // Get other sql parameters from the configuration object,
        // if any.
        Iterator<String> paramIt = configuration.getKeys("sqlParameters");
        while (paramIt.hasNext()) {
            String rawName = paramIt.next();
            String paramName = paramIt.next().substring("sqlParameters.[@".length(), rawName.length() - 1);
            String paramValue = configuration.getString(rawName);
            m_sqlParameters.put(paramName, paramValue);
        }
    }

    /**
     * Produces the complete list of User names, with correct case.
     * 
     * @return a <code>List</code> of <code>String</code>s representing user
     *         names.
     */
    protected List<String> listUserNames() throws UsersRepositoryException {
        Collection<User> users = getAllUsers();
        List<String> userNames = new ArrayList<>(users.size());
        for (User user : users) {
            userNames.add(user.getUserName());
        }
        users.clear();
        return userNames;
    }

    /**
     * Returns a list populated with all of the Users in the repository.
     * 
     * @return an <code>Iterator</code> of <code>User</code>s.
     */
    protected Iterator<User> listAllUsers() throws UsersRepositoryException {
        return getAllUsers().iterator();
    }

    /**
     * Returns a list populated with all of the Users in the repository.
     * 
     * @return a <code>Collection</code> of <code>JamesUser</code>s.
     * @throws UsersRepositoryException
     */
    private Collection<User> getAllUsers() throws UsersRepositoryException {
        List<User> userList = new ArrayList<>(); // Build the users into
                                                     // this list.

        Connection conn = null;
        PreparedStatement getUsersStatement = null;
        ResultSet rsUsers = null;
        try {
            conn = openConnection();
            // Get a ResultSet containing all users.
            getUsersStatement = conn.prepareStatement(m_getUsersSql);
            rsUsers = getUsersStatement.executeQuery();

            // Loop through and build a User for every row.
            while (rsUsers.next()) {
                User user = readUserFromResultSet(rsUsers);
                userList.add(user);
            }
        } catch (SQLException sqlExc) {
            throw new UsersRepositoryException("Error accessing database", sqlExc);
        } finally {
            theJDBCUtil.closeJDBCResultSet(rsUsers);
            theJDBCUtil.closeJDBCStatement(getUsersStatement);
            theJDBCUtil.closeJDBCConnection(conn);
        }

        return userList;
    }

    /**
     * Adds a user to the underlying Repository. The user name must not clash
     * with an existing user.
     * 
     * @param user
     *            the user to add
     * @throws UsersRepositoryException
     */
    protected void doAddUser(User user) throws UsersRepositoryException {
        Connection conn = null;
        PreparedStatement addUserStatement = null;

        // Insert into the database.
        try {
            conn = openConnection();
            // Get a PreparedStatement for the insert.
            addUserStatement = conn.prepareStatement(m_insertUserSql);

            setUserForInsertStatement(user, addUserStatement);

            addUserStatement.execute();
        } catch (SQLException sqlExc) {
            throw new UsersRepositoryException("Error accessing database", sqlExc);
        } finally {
            theJDBCUtil.closeJDBCStatement(addUserStatement);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Removes a user from the underlying repository. If the user doesn't exist,
     * returns ok.
     * 
     * @param user
     *            the user to remove
     * @throws UsersRepositoryException
     */
    protected void doRemoveUser(User user) throws UsersRepositoryException {
        String username = user.getUserName();

        Connection conn = null;
        PreparedStatement removeUserStatement = null;

        // Delete from the database.
        try {
            conn = openConnection();
            removeUserStatement = conn.prepareStatement(m_deleteUserSql);
            removeUserStatement.setString(1, username);
            removeUserStatement.execute();
        } catch (SQLException sqlExc) {
            throw new UsersRepositoryException("Error accessing database", sqlExc);
        } finally {
            theJDBCUtil.closeJDBCStatement(removeUserStatement);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Updates a user record to match the supplied User.
     * 
     * @param user
     *            the user to update
     * @throws UsersRepositoryException
     */
    protected void doUpdateUser(User user) throws UsersRepositoryException {
        Connection conn = null;
        PreparedStatement updateUserStatement = null;

        // Update the database.
        try {
            conn = openConnection();
            updateUserStatement = conn.prepareStatement(m_updateUserSql);
            setUserForUpdateStatement(user, updateUserStatement);
            updateUserStatement.execute();
        } catch (SQLException sqlExc) {
            throw new UsersRepositoryException("Error accessing database", sqlExc);
        } finally {
            theJDBCUtil.closeJDBCStatement(updateUserStatement);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Gets a user by name, ignoring case if specified. This implementation gets
     * the entire set of users, and scrolls through searching for one matching
     * <code>name</code>.
     * 
     * @param name
     *            the name of the user being retrieved
     * @param ignoreCase
     *            whether the name is regarded as case-insensitive
     * 
     * @return the user being retrieved, null if the user doesn't exist
     * @throws UsersRepositoryException
     */
    protected User getUserByNameIterating(String name, boolean ignoreCase) throws UsersRepositoryException {
        // Just iterate through all of the users until we find one matching.
        Iterator<User> users = listAllUsers();
        while (users.hasNext()) {
            User user = users.next();
            String username = user.getUserName();
            if ((!ignoreCase && username.equals(name)) || (ignoreCase && username.equalsIgnoreCase(name))) {
                return user;
            }
        }
        // Not found - return null
        return null;
    }

    /**
     * Gets a user by name, ignoring case if specified. If the specified SQL
     * statement has been defined, this method overrides the basic
     * implementation in AbstractJamesUsersRepository to increase performance.
     * 
     * @param name
     *            the name of the user being retrieved
     * @param ignoreCase
     *            whether the name is regarded as case-insensitive
     * 
     * @return the user being retrieved, null if the user doesn't exist
     * @throws UsersRepositoryException
     */
    protected User getUserByName(String name, boolean ignoreCase) throws UsersRepositoryException {
        // See if this statement has been set, if not, use
        // simple superclass method.
        if (m_userByNameCaseInsensitiveSql == null) {
            return getUserByNameIterating(name, ignoreCase);
        }

        // Always get the user via case-insensitive SQL,
        // then check case if necessary.
        Connection conn = null;
        PreparedStatement getUsersStatement = null;
        ResultSet rsUsers = null;
        try {
            conn = openConnection();
            // Get a ResultSet containing all users.
            String sql = m_userByNameCaseInsensitiveSql;
            getUsersStatement = conn.prepareStatement(sql);

            getUsersStatement.setString(1, name.toLowerCase(Locale.US));

            rsUsers = getUsersStatement.executeQuery();

            // For case-insensitive matching, the first matching user will be
            // returned.
            User user = null;
            while (rsUsers.next()) {
                User rowUser = readUserFromResultSet(rsUsers);
                String actualName = rowUser.getUserName();

                // Check case before we assume it's the right one.
                if (ignoreCase || actualName.equals(name)) {
                    user = rowUser;
                    break;
                }
            }
            return user;
        } catch (SQLException sqlExc) {
            throw new UsersRepositoryException("Error accessing database", sqlExc);
        } finally {
            theJDBCUtil.closeJDBCResultSet(rsUsers);
            theJDBCUtil.closeJDBCStatement(getUsersStatement);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Reads properties for a User from an open ResultSet. Subclass
     * implementations of this method must have knowledge of the fields
     * presented by the "select" and "selectByLowercaseName" SQL statements.
     * These implemenations may generate a subclass-specific User instance.
     * 
     * @param rsUsers
     *            A ResultSet with a User record in the current row.
     * @return A User instance
     * @throws SQLException
     *             if an exception occurs reading from the ResultSet
     */
    protected abstract User readUserFromResultSet(ResultSet rsUsers) throws SQLException;

    /**
     * Set parameters of a PreparedStatement object with property values from a
     * User instance. Implementations of this method have knowledge of the
     * parameter ordering of the "insert" SQL statement definition.
     * 
     * @param user
     *            a User instance, which should be an implementation class which
     *            is handled by this Repostory implementation.
     * @param userInsert
     *            a PreparedStatement initialised with SQL taken from the
     *            "insert" SQL definition.
     * @throws SQLException
     *             if an exception occurs while setting parameter values.
     */
    protected abstract void setUserForInsertStatement(User user, PreparedStatement userInsert) throws SQLException;

    /**
     * Set parameters of a PreparedStatement object with property values from a
     * User instance. Implementations of this method have knowledge of the
     * parameter ordering of the "update" SQL statement definition.
     * 
     * @param user
     *            a User instance, which should be an implementation class which
     *            is handled by this Repostory implementation.
     * @param userUpdate
     *            a PreparedStatement initialised with SQL taken from the
     *            "update" SQL definition.
     * @throws SQLException
     *             if an exception occurs while setting parameter values.
     */
    protected abstract void setUserForUpdateStatement(User user, PreparedStatement userUpdate) throws SQLException;

    /**
     * Opens a connection, throwing a runtime exception if a SQLException is
     * encountered in the process.
     * 
     * @return the new connection
     * @throws SQLException
     */
    private Connection openConnection() throws SQLException {
        return m_datasource.getConnection();

    }
}
