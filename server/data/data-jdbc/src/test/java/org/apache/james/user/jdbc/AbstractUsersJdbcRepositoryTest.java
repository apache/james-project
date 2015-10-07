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

import org.apache.james.user.lib.AbstractUsersRepositoryTest;

public abstract class AbstractUsersJdbcRepositoryTest extends AbstractUsersRepositoryTest {
    
    /* Deactivate this test for the Jdbc implementation
     * Should be disable via @Ignore with JUnit4
     * 
     * @see org.apache.james.user.lib.AbstractUsersRepositoryTest#testUsersRepositoryEmpty()
     */
    @Override
    public void testUserListing() {
    }

    /* Deactivate this test for the Jdbc implementation
     * Should be disable via @Ignore with JUnit4
     * 
     * @see org.apache.james.user.lib.AbstractUsersRepositoryTest#testUsersRepositoryEmpty()
     */
    @Override
    public void testUpperCaseSameUser() {
    }

    /* Deactivate this test for the Jdbc implementation
     * Should be disable via @Ignore with JUnit4
     * 
     * @see org.apache.james.user.lib.AbstractUsersRepositoryTest#testUsersRepositoryEmpty()
     */
    @Override
    public void testUserAddedIsFound() {
    }

    /* Deactivate this test for the Jdbc implementation
     * Should be disable via @Ignore with JUnit4
     * 
     * @see org.apache.james.user.lib.AbstractUsersRepositoryTest#testUsersRepositoryEmpty()
     */
    @Override
    public void testUserPassword() {
    }

    /* Deactivate this test for the Jdbc implementation
     * Should be disable via @Ignore with JUnit4
     * 
     * @see org.apache.james.user.lib.AbstractUsersRepositoryTest#testUsersRepositoryEmpty()
     */
    @Override
    public void testUserAddRemoveCycle() {
    }

}
