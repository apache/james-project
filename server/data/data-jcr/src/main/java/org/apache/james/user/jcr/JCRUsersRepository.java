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

package org.apache.james.user.jcr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.Text;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.jcr.model.JCRUser;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link UsersRepository} implementation which stores users to a JCR
 * {@link Repository}
 */
public class JCRUsersRepository extends AbstractUsersRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JCRUsersRepository.class);

    // TODO: Add namespacing
    private static final String PASSWD_PROPERTY = "passwd";

    private static final String USERNAME_PROPERTY = "username";
    private static final String USERS_PATH = "users";

    private Repository repository;
    private SimpleCredentials creds;
    private String workspace;

    @Inject
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void doConfigure(HierarchicalConfiguration config) throws ConfigurationException {
        this.workspace = config.getString("workspace", null);
        String username = config.getString("username", null);
        String password = config.getString("password", null);

        if (username != null && password != null) {
            this.creds = new SimpleCredentials(username, password.toCharArray());
        }
    }

    protected String toSafeName(String key) {
        return ISO9075.encode(Text.escapeIllegalJcrChars(key));
    }

    private Session login() throws RepositoryException {
        return repository.login(creds, workspace);
    }

    /**
     * Get the user object with the specified user name. Return null if no such
     * user.
     * 
     * @param username
     *            the name of the user to retrieve
     * @return the user being retrieved, null if the user doesn't exist
     * 
     */
    public User getUserByName(String username) {
        User user;
        try {
            final Session session = login();
            try {
                final String name = toSafeName(username);
                final String path = USERS_PATH + "/" + name;
                final Node rootNode = session.getRootNode();

                try {
                    final Node node = rootNode.getNode(path);
                    user = new JCRUser(node.getProperty(USERNAME_PROPERTY).getString(), node.getProperty(PASSWD_PROPERTY).getString());
                } catch (PathNotFoundException e) {
                    // user not found
                    user = null;
                }
            } finally {
                session.logout();
            }

        } catch (RepositoryException e) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Failed to add user: " + username, e);
            }
            user = null;
        }
        return user;
    }

    /**
     * Returns the user name of the user matching name on an equalsIgnoreCase
     * basis. Returns null if no match.
     * 
     * @param name
     *            the name to case-correct
     * @return the case-correct name of the user, null if the user doesn't exist
     */
    public String getRealName(String name) {
        return null;
    }

    /**
     * Update the repository with the specified user object. A user object with
     * this username must already exist.
     * 
     * @param user
     *            the user
     * @throws UsersRepositoryException
     *            If an error occurred
     */
    public void updateUser(User user) throws UsersRepositoryException {
        if (user != null && user instanceof JCRUser) {
            final JCRUser jcrUser = (JCRUser) user;
            final String userName = jcrUser.getUserName();
            try {
                final Session session = login();
                try {
                    final String name = toSafeName(userName);
                    final String path = USERS_PATH + "/" + name;
                    final Node rootNode = session.getRootNode();

                    try {
                        final String hashedSaltedPassword = jcrUser.getHashedSaltedPassword();
                        rootNode.getNode(path).setProperty(PASSWD_PROPERTY, hashedSaltedPassword);
                        session.save();
                    } catch (PathNotFoundException e) {
                        // user not found
                        LOGGER.debug("User not found");
                        throw new UsersRepositoryException("User " + user.getUserName() + " not exist");
                    }
                } finally {
                    session.logout();
                }

            } catch (RepositoryException e) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Failed to add user: " + userName, e);
                }
                throw new UsersRepositoryException("Failed to add user: " + userName, e);

            }
        }
    }

    /**
     * Removes a user from the repository
     * 
     * @param username
     *            the user to remove from the repository
     * @throws UsersRepositoryException
     */
    public void removeUser(String username) throws UsersRepositoryException {
        try {
            final Session session = login();
            try {
                final String name = toSafeName(username);
                final String path = USERS_PATH + "/" + name;
                try {
                    session.getRootNode().getNode(path).remove();
                    session.save();
                } catch (PathNotFoundException e) {
                    // user not found
                    throw new UsersRepositoryException("User " + username + " not exists");
                }
            } finally {
                session.logout();
            }

        } catch (RepositoryException e) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Failed to remove user: " + username, e);
            }
            throw new UsersRepositoryException("Failed to remove user: " + username, e);

        }
    }

    /**
     * Returns whether or not this user is in the repository
     * 
     * @param name
     *            the name to check in the repository
     * @return whether the user is in the repository
     * @throws UsersRepositoryException
     */
    public boolean contains(String name) throws UsersRepositoryException {
        try {
            final Session session = login();
            try {
                final Node rootNode = session.getRootNode();
                final String path = USERS_PATH + "/" + toSafeName(name.toLowerCase(Locale.US));
                rootNode.getNode(path);
                return true;
            } finally {
                session.logout();
            }
        } catch (PathNotFoundException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("User not found: " + name, e);
            }
        } catch (RepositoryException e) {
            throw new UsersRepositoryException("Failed to search for user: " + name, e);

        }

        return false;
    }

    /**
     * Test if user with name 'name' has password 'password'.
     * 
     * @param username
     *            the name of the user to be tested
     * @param password
     *            the password to be tested
     * 
     * @return true if the test is successful, false if the user doesn't exist
     *         or if the password is incorrect
     * @throws UsersRepositoryException
     * 
     * @since James 1.2.2
     */
    public boolean test(String username, String password) throws UsersRepositoryException {
        try {
            final Session session = login();
            try {
                final String name = toSafeName(username);
                final String path = USERS_PATH + "/" + name;
                final Node rootNode = session.getRootNode();

                try {
                    final Node node = rootNode.getNode(path);
                    final String current = node.getProperty(PASSWD_PROPERTY).getString();
                    if (current == null || current.equals("")) {
                        return password == null || password.equals("");
                    }
                    final String hashPassword = JCRUser.hashPassword(username, password);
                    return current.equals(hashPassword);
                } catch (PathNotFoundException e) {
                    // user not found
                    LOGGER.debug("User not found");
                    return false;
                }
            } finally {
                session.logout();
            }

        } catch (RepositoryException e) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Failed to search user: " + username, e);
            }
            throw new UsersRepositoryException("Failed to search for user: " + username, e);

        }

    }

    /**
     * Returns a count of the users in the repository.
     * 
     * @return the number of users in the repository
     * @throws UsersRepositoryException
     */
    public int countUsers() throws UsersRepositoryException {
        try {
            final Session session = login();
            try {
                final Node rootNode = session.getRootNode();
                try {
                    final Node node = rootNode.getNode(USERS_PATH);
                    // TODO: Use query
                    // TODO: Use namespacing to avoid unwanted nodes
                    NodeIterator it = node.getNodes();
                    return (int) it.getSize();
                } catch (PathNotFoundException e) {
                    return 0;
                }
            } finally {
                session.logout();
            }
        } catch (RepositoryException e) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Failed to count user", e);
            }
            throw new UsersRepositoryException("Failed to count user", e);

        }
    }

    /**
     * List users in repository.
     * 
     * @return Iterator over a collection of Strings, each being one user in the
     *         repository.
     * @throws UsersRepositoryException
     */
    public Iterator<String> list() throws UsersRepositoryException {
        final Collection<String> userNames = new ArrayList<>();
        try {
            final Session session = login();
            try {
                final Node rootNode = session.getRootNode();
                try {
                    final Node baseNode = rootNode.getNode(USERS_PATH);
                    // TODO: Use query
                    final NodeIterator it = baseNode.getNodes();
                    while (it.hasNext()) {
                        final Node node = it.nextNode();
                        try {
                            final String userName = node.getProperty(USERNAME_PROPERTY).getString();
                            userNames.add(userName);
                        } catch (PathNotFoundException e) {
                            LOGGER.info("Node missing user name. Ignoring.");
                        }
                    }
                } catch (PathNotFoundException e) {
                    LOGGER.info("Path not found. Forgotten to setup the repository?");
                }
            } finally {
                session.logout();
            }
        } catch (RepositoryException e) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Failed to list users", e);
            }
            throw new UsersRepositoryException("Failed to list users", e);
        }
        return userNames.iterator();
    }

    @Override
    protected void doAddUser(String username, String password) throws UsersRepositoryException {
        String lowerCasedUsername = username.toLowerCase(Locale.US);
        if (contains(lowerCasedUsername)) {
            throw new UsersRepositoryException(lowerCasedUsername + " already exists.");
        }
        try {
            final Session session = login();
            try {
                final String name = toSafeName(lowerCasedUsername);
                final String path = USERS_PATH + "/" + name;
                final Node rootNode = session.getRootNode();
                try {
                    rootNode.getNode(path);
                    LOGGER.info("User already exists");
                    throw new UsersRepositoryException("User " + lowerCasedUsername + " already exists");
                } catch (PathNotFoundException e) {
                    // user does not exist
                }
                Node parent;
                try {
                    parent = rootNode.getNode(USERS_PATH);
                } catch (PathNotFoundException e) {
                    // TODO: Need to consider whether should insist that parent
                    // TODO: path exists.
                    parent = rootNode.addNode(USERS_PATH);
                }

                Node node = parent.addNode(name);
                node.setProperty(USERNAME_PROPERTY, lowerCasedUsername);
                final String hashedPassword;
                if (password == null) {
                    // Support easy password reset
                    hashedPassword = "";
                } else {
                    hashedPassword = JCRUser.hashPassword(lowerCasedUsername, password);
                }
                node.setProperty(PASSWD_PROPERTY, hashedPassword);
                session.save();
            } finally {
                session.logout();
            }

        } catch (RepositoryException e) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Failed to add user: " + lowerCasedUsername, e);
            }
            throw new UsersRepositoryException("Failed to add user: " + lowerCasedUsername, e);

        }

    }

}
