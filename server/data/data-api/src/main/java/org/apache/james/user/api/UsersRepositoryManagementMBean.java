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

package org.apache.james.user.api;

/**
 * Expose user account management functionality through JMX.
 */
public interface UsersRepositoryManagementMBean {

    /**
     * Adds a user to this mail server.
     * 
     * 
     * @param userName
     *            The name of the user being added
     * @param password
     *            The password of the user being added
     */
    void addUser(String userName, String password) throws Exception;

    /**
     * Deletes a user from this mail server.
     * 
     * 
     * @param userName
     *            The name of the user being deleted
     * @throws UsersRepositoryException
     *             if error
     */
    void deleteUser(String userName) throws Exception;

    /**
     * Check if a user exists with the given name.
     * 
     * 
     * @param userName
     *            The name of the user
     * @return TRUE, if the user exists
     * @throws UsersRepositoryException
     *             if error
     */
    boolean verifyExists(String userName) throws Exception;

    /**
     * Total count of existing users
     * 
     * 
     * @return Total count of existing users
     */
    long countUsers() throws Exception;

    /**
     * List the names of all users
     * 
     * 
     * @return List of all user names
     * @throws UsersRepositoryException
     *             if error
     */
    String[] listAllUsers() throws Exception;

    /**
     * Set a user's password
     * 
     * 
     * @param userName
     *            The name of the user whose password will be changed
     * @param password
     *            The new password
     * @throws UsersRepositoryException
     *             if error
     */
    void setPassword(String userName, String password) throws Exception;

    /**
     * Removes a user's alias which terminates local mail forwarding
     * 
     * 
     * @param userName
     *            The name of the user whose alias is unset
     * @throws UsersRepositoryException
     *             if error
     */
    @Deprecated
    void unsetAlias(String userName) throws Exception;

    /**
     * Retrieves the user's alias, if set
     * 
     * 
     * @return User's alias, or NULL, if no alias is set
     * @throws UsersRepositoryException
     *             if error
     */
    @Deprecated
    String getAlias(String userName) throws Exception;

    /**
     * Removes a user's forward email address which terminates remote mail
     * forwarding
     * 
     * 
     * @param userName
     *            The name of the user whose forward is unset
     * @throws UsersRepositoryException
     *             if error
     */
    @Deprecated
    void unsetForwardAddress(String userName) throws Exception;

    /**
     * Retrieves the user's forward, if set
     * 
     * 
     * @param userName
     *            The name of the user whose forward is set
     * @return User's forward email address, or NULL, if no forward is set
     * @throws UsersRepositoryException
     *             if error
     */
    @Deprecated
    String getForwardAddress(String userName) throws Exception;

    /**
     * Return true if the UserRepository has VirtualHosting enabled
     * 
     * @return virtual
     * @throws UsersRepositoryException
     */
    boolean getVirtualHostingEnabled() throws Exception;

}
