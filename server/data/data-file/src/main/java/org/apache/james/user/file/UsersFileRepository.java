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
import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.repository.file.FilePersistentObjectRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.AbstractJamesUsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@Singleton
public class UsersFileRepository extends AbstractJamesUsersRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(UsersFileRepository.class);

    private FilePersistentObjectRepository objectRepository;

    /**
     * The destination URL used to define the repository.
     */
    private String destination;

    private FileSystem fileSystem;

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    protected void doConfigure(HierarchicalConfiguration configuration) throws ConfigurationException {
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
            objectRepository.setFileSystem(fileSystem);
            objectRepository.configure(objectConfiguration);
            objectRepository.init();
            LOGGER.debug("{} created in {}", getClass().getName(), destination);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize repository", e);
            throw e;
        }
    }

    @Override
    public Iterator<String> list() {
        return objectRepository.list();
    }

    @Override
    protected void doAddUser(User user) throws UsersRepositoryException {
        if (contains(user.getUserName())) {
            throw new UsersRepositoryException(user.getUserName() + " already exists.");
        }
        try {
            objectRepository.put(user.getUserName(), user);
        } catch (Exception e) {
            throw new UsersRepositoryException("Exception caught while storing user", e);
        }
    }

    @Override
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
                throw new UsersRepositoryException("Exception while retrieving user", e);
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

    @Override
    protected void doUpdateUser(User user) throws UsersRepositoryException {
        try {
            objectRepository.put(user.getUserName(), user);
        } catch (Exception e) {
            throw new UsersRepositoryException("Exception caught while storing user", e);
        }
    }

    @Override
    public synchronized void removeUser(String name) throws UsersRepositoryException {
        if (!objectRepository.remove(name)) {
            throw new UsersRepositoryException("User " + name + " does not exist");
        }
    }

    @Override
    public boolean contains(String name) throws UsersRepositoryException {
        if (ignoreCase) {
            return containsCaseInsensitive(name);
        } else {
            return objectRepository.containsKey(name);
        }
    }

    @Deprecated
    private boolean containsCaseInsensitive(String name) {
        Iterator<String> it = list();
        while (it.hasNext()) {
            if (name.equalsIgnoreCase(it.next())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean test(String name, String password) throws UsersRepositoryException {
        User user;
        try {
            user = getUserByName(name);
            if (user == null) {
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception retrieving User", e);
        }
        return user.verifyPassword(password);
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        int count = 0;
        for (Iterator<String> it = list(); it.hasNext(); it.next()) {
            count++;
        }
        return count;
    }

}
