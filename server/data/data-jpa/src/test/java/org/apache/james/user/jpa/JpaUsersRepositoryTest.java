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
package org.apache.james.user.jpa;

import java.util.HashMap;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.jpa.model.JPAUser;
import org.apache.james.user.lib.AbstractUsersRepositoryTest;
import org.apache.openjpa.persistence.OpenJPAEntityManager;
import org.apache.openjpa.persistence.OpenJPAEntityManagerFactory;
import org.apache.openjpa.persistence.OpenJPAEntityTransaction;
import org.apache.openjpa.persistence.OpenJPAPersistence;
import org.junit.After;
import org.junit.Before;
import org.slf4j.LoggerFactory;

public class JpaUsersRepositoryTest extends AbstractUsersRepositoryTest {

    private HashMap<String, String> properties;
    private OpenJPAEntityManagerFactory factory;

    @Before
    @Override
    public void setUp() throws Exception {
        properties = new HashMap<String, String>();
        properties.put("openjpa.ConnectionDriverName", "org.h2.Driver");
        properties.put("openjpa.ConnectionURL", "jdbc:h2:target/users/db");
        properties.put("openjpa.Log", "JDBC=WARN, SQL=WARN, Runtime=WARN");
        properties.put("openjpa.ConnectionFactoryProperties", "PrettyPrint=true, PrettyPrintLineLength=72");
        properties.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)");
        properties.put("openjpa.MetaDataFactory", "jpa(Types=" + JPAUser.class.getName() + ")");
        super.setUp();
        deleteAll();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        deleteAll();
        super.tearDown();

    }

    private void deleteAll() {
        OpenJPAEntityManager manager = factory.createEntityManager();
        final OpenJPAEntityTransaction transaction = manager.getTransaction();
        try {
            transaction.begin();
            manager.createQuery("DELETE FROM JamesUser user").executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            e.printStackTrace();
            if (transaction.isActive()) {
                transaction.rollback();
            }
        } finally {
            manager.close();
        }
    }

    @Override
    protected UsersRepository getUsersRepository() throws Exception {
        factory = OpenJPAPersistence.getEntityManagerFactory(properties);
        JPAUsersRepository repos = new JPAUsersRepository();
        repos.setLog(LoggerFactory.getLogger("JPA"));
        repos.setEntityManagerFactory(factory);
        repos.configure(new DefaultConfigurationBuilder());
        return repos;
    }
}
