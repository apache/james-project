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

package org.apache.james.user.file;

import java.util.Iterator;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.repository.file.FilePersistentObjectRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.AbstractJamesUsersRepository;

/**
 * <p>
 * Implementation of a Repository to store users on the File System.
 * </p>
 * 
 * <p>
 * Requires a configuration element in the .conf.xml file of the form:
 * 
 * <pre>
 *  &lt;repository destinationURL="file://path-to-root-dir-for-repository"
 *              type="USERS"
 *              model="SYNCHRONOUS"/&gt;
 * </pre>
 * 
 * Requires a logger called UsersRepository.
 * </p>
 */
@Deprecated
public class UsersFileRepository extends AbstractJamesUsersRepository {

    /**
     * Whether 'deep debugging' is turned on.
     */
    protected static boolean DEEP_DEBUG = false;

    private FilePersistentObjectRepository objectRepository;

    /**
     * The destination URL used to define the repository.
     */
    private String destination;

    private FileSystem fileSystem;

    @Inject
    public void setFileSystem(@Named("filesystem") FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    /**
     * @see org.apache.james.user.lib.AbstractJamesUsersRepository#doConfigure(org.apache.commons.configuration.HierarchicalConfiguration)
     */
    protected void doConfigure(final HierarchicalConfiguration configuration) throws ConfigurationException {
        super.doConfigure(configuration);
        destination = configuration.getString("destination.[@URL]");

        String urlSeparator = "/";
        if (!destination.endsWith(urlSeparator)) {
            destination += urlSeparator;
        }
    }

    @PostConstruct
    public void init() throws Exception {
        try {
            // TODO Check how to remove this!
            // prepare Configurations for object and stream repositories
            final DefaultConfigurationBuilder objectConfiguration = new DefaultConfigurationBuilder();

            objectConfiguration.addProperty("[@destinationURL]", destination);

            objectRepository = new FilePersistentObjectRepository();
            objectRepository.setLog(getLogger());
            objectRepository.setFileSystem(fileSystem);
            objectRepository.configure(objectConfiguration);
            objectRepository.init();
            if (getLogger().isDebugEnabled()) {
                String logBuffer = this.getClass().getName() + " created in " + destination;
                getLogger().debug(logBuffer);
            }
        } catch (Exception e) {
            if (getLogger().isErrorEnabled()) {
                getLogger().error("Failed to initialize repository:" + e.getMessage(), e);
            }
            throw e;
        }
    }

    /**
     * @see org.apache.james.user.api.UsersRepository#list()
     */
    public Iterator<String> list() {
        return objectRepository.list();
    }

    /**
     * @see org.apache.james.user.lib.AbstractJamesUsersRepository#doAddUser(org.apache.james.user.api.model.User)
     */
    protected void doAddUser(User user) throws UsersRepositoryException {
        if (contains(user.getUserName())) {
            throw new UsersRepositoryException(user.getUserName() + " already exists.");
        }
        try {
            objectRepository.put(user.getUserName(), user);
        } catch (Exception e) {
            throw new UsersRepositoryException("Exception caught while storing user: " + e);
        }
    }

    /**
     * @throws UsersRepositoryException
     * @see org.apache.james.user.api.UsersRepository#getUserByName(java.lang.String)
     */
    public synchronized User getUserByName(String name) throws UsersRepositoryException {
        if (ignoreCase) {
            name = getRealName(name);
            if (name == null) {
                return null;
            }
        }
        if (contains(name)) {
            try {
                return (User) objectRepository.get(name);
            } catch (Exception e) {
                throw new UsersRepositoryException("Exception while retrieving user: " + e.getMessage());
            }
        } else {
            return null;
        }
    }

    /**
     * Return the real name, given the ignoreCase boolean parameter
     * 
     * @param name
     * @param ignoreCase
     * @return The real name
     * @throws UsersRepositoryException
     */
    private String getRealName(String name, boolean ignoreCase) {
        if (ignoreCase) {
            Iterator<String> it = list();
            while (it.hasNext()) {
                String temp = it.next();
                if (name.equalsIgnoreCase(temp)) {
                    return temp;
                }
            }
            return null;
        } else {
            return objectRepository.containsKey(name) ? name : null;
        }
    }

    /**
     * Return the real name, given the ignoreCase boolean parameter
     * 
     * @param name
     * @return The real name
     * @throws UsersRepositoryException
     */
    private String getRealName(String name) throws UsersRepositoryException {
        return getRealName(name, ignoreCase);
    }

    /**
     * @throws UsersRepositoryException
     * @see org.apache.james.user.lib.AbstractJamesUsersRepository#doUpdateUser(org.apache.james.user.api.model.User)
     */
    protected void doUpdateUser(User user) throws UsersRepositoryException {
        try {
            objectRepository.put(user.getUserName(), user);
        } catch (Exception e) {
            throw new UsersRepositoryException("Exception caught while storing user: " + e);
        }
    }

    /**
     * @see org.apache.james.user.api.UsersRepository#removeUser(java.lang.String)
     */
    public synchronized void removeUser(String name) throws UsersRepositoryException {
        objectRepository.remove(name);
    }

    /**
     * @see org.apache.james.user.api.UsersRepository#contains(java.lang.String)
     */
    public boolean contains(String name) throws UsersRepositoryException {
        if (ignoreCase) {
            return containsCaseInsensitive(name);
        } else {
            return objectRepository.containsKey(name);
        }
    }

    /*
     * This is not longer in the api (deprecated)
     * @see org.apache.james.user.api.UsersRepository#containsCaseInsensitive(java.lang.String)
     */
    private boolean containsCaseInsensitive(String name) {
        Iterator<String> it = list();
        while (it.hasNext()) {
            if (name.equalsIgnoreCase(it.next())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @see org.apache.james.user.api.UsersRepository#test(java.lang.String,
     *      java.lang.String)
     */
    public boolean test(String name, String password) throws UsersRepositoryException {
        User user;
        try {
            user = getUserByName(name);
            if (user == null)
                return false;
        } catch (Exception e) {
            throw new RuntimeException("Exception retrieving User" + e);
        }
        return user.verifyPassword(password);
    }

    /**
     * @see org.apache.james.user.api.UsersRepository#countUsers()
     */
    public int countUsers() throws UsersRepositoryException {
        int count = 0;
        for (Iterator<String> it = list(); it.hasNext(); it.next()) {
            count++;
        }
        return count;
    }

}
