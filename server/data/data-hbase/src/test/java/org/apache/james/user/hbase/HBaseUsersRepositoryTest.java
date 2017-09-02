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
package org.apache.james.user.hbase;

import java.io.IOException;
import java.util.Iterator;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.mailbox.hbase.HBaseClusterSingleton;
import org.apache.james.system.hbase.TablePool;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.AbstractUsersRepositoryTest;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for the HBase UsersRepository implementation.
 *
 * Simply create the needed HBaseUsersRepository instance, and let the
 * AbstractUsersRepositoryTest run the tests
 */
public class HBaseUsersRepositoryTest extends AbstractUsersRepositoryTest {

    private static final HBaseClusterSingleton cluster = HBaseClusterSingleton.build();

    @BeforeClass
    public static void setMeUp() throws IOException {
        TablePool.getInstance(cluster.getConf());
    }

    /**
     * @see org.apache.james.user.lib.AbstractUsersRepositoryTest#setUp()
     */
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        deleteAll();
    }

    /**
     * Delete all users in the repository. Used between each tests.
     *
     * @throws UsersRepositoryException
     * @throws Exception
     */
    private void deleteAll() throws Exception {
        Iterator<String> it = getUsersRepository().list();
        while (it.hasNext()) {
            getUsersRepository().removeUser(it.next());
        }
    }

    /**
     * @see org.apache.james.user.lib.AbstractUsersRepositoryTest#getUsersRepository()
     */
    @Override
    protected AbstractUsersRepository getUsersRepository() throws Exception {
        HBaseUsersRepository userRepository = new HBaseUsersRepository();
        userRepository.configure(new DefaultConfigurationBuilder());
        return userRepository;
    }
    
    @Override
    @Ignore
    @Test(expected = UsersRepositoryException.class)
    public void removeUserShouldThrowWhenUserNotInRepository() throws UsersRepositoryException {
    }
}
