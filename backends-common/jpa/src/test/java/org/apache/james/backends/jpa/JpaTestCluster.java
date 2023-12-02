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

package org.apache.james.backends.jpa;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.openjpa.persistence.OpenJPAPersistence;

import com.google.common.collect.ImmutableList;

public class JpaTestCluster {

    public static JpaTestCluster create(Class<?>... clazz) {
        return create(ImmutableList.copyOf(clazz));
    }

    public static JpaTestCluster create(List<Class<?>> clazz) {
        HashMap<String, String> properties = new HashMap<>();
        properties.put("openjpa.ConnectionDriverName", org.apache.derby.jdbc.EmbeddedDriver.class.getName());
        properties.put("openjpa.ConnectionURL", "jdbc:derby:memory:mailboxintegrationtesting;create=true"); // Memory Derby database
        properties.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)"); // Create Foreign Keys
        properties.put("openjpa.jdbc.SchemaFactory", "native(ForeignKeys=true)");
        properties.put("openjpa.jdbc.MappingDefaults", "ForeignKeyDeleteAction=cascade, JoinForeignKeyDeleteAction=cascade");
        properties.put("openjpa.jdbc.QuerySQLCache", "false");
        properties.put("openjpa.Log", "JDBC=WARN, SQL=WARN, Runtime=WARN");
        properties.put("openjpa.ConnectionFactoryProperties", "PrettyPrint=true, PrettyPrintLineLength=72");
        properties.put("openjpa.MetaDataFactory", "jpa(Types=" +
                clazz.stream()
                    .map(Class::getName)
                    .collect(Collectors.joining(";"))
            + ")");
        return new JpaTestCluster(OpenJPAPersistence.getEntityManagerFactory(properties));
    }

    private final EntityManagerFactory entityManagerFactory;

    private JpaTestCluster(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    public void clear(String... tables) {
        clear(ImmutableList.copyOf(tables));
    }

    public void clear(List<String> tables) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        entityManager.getTransaction().begin();
        for (String tableName: tables) {
            entityManager.createNativeQuery("DELETE FROM " + tableName).executeUpdate();
        }
        entityManager.getTransaction().commit();
        EntityManagerUtils.safelyClose(entityManager);
    }
}
