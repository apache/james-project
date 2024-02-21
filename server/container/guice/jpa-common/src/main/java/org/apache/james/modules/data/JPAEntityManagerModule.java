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
package org.apache.james.modules.data;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Singleton;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.jpa.JPAConfiguration;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.base.Joiner;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class JPAEntityManagerModule extends AbstractModule {
    @Provides
    @Singleton
    public EntityManagerFactory provideEntityManagerFactory(JPAConfiguration jpaConfiguration) {
        HashMap<String, String> properties = new HashMap<>();

        properties.put(JPAConfiguration.JPA_CONNECTION_DRIVER_NAME, jpaConfiguration.getDriverName());
        properties.put(JPAConfiguration.JPA_CONNECTION_URL, jpaConfiguration.getDriverURL());
        jpaConfiguration.getCredential()
            .ifPresent(credential -> {
                properties.put(JPAConfiguration.JPA_CONNECTION_USERNAME, credential.getUsername());
                properties.put(JPAConfiguration.JPA_CONNECTION_PASSWORD, credential.getPassword());
            });

        List<String> connectionProperties = new ArrayList<>();
        jpaConfiguration.isTestOnBorrow().ifPresent(testOnBorrow -> connectionProperties.add("TestOnBorrow=" + testOnBorrow));
        jpaConfiguration.getValidationQueryTimeoutSec()
            .ifPresent(timeoutSecond -> connectionProperties.add("ValidationTimeout=" + timeoutSecond * 1000));
        jpaConfiguration.getValidationQuery()
            .ifPresent(validationQuery -> connectionProperties.add("ValidationSQL='" + validationQuery + "'"));
        jpaConfiguration.getMaxConnections()
                .ifPresent(maxConnections -> connectionProperties.add("MaxTotal=" + maxConnections));

        connectionProperties.addAll(jpaConfiguration.getCustomDatasourceProperties().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.toList()));
        properties.put(JPAConfiguration.JPA_CONNECTION_PROPERTIES, Joiner.on(",").join(connectionProperties));
        properties.putAll(jpaConfiguration.getCustomOpenjpaProperties());

        jpaConfiguration.isMultithreaded()
                .map(Object::toString)
                .ifPresent(value -> properties.put(JPAConfiguration.JPA_MULTITHREADED, value));
        jpaConfiguration.isAttachmentStorageEnabled()
                .map(Object::toString)
                .ifPresent(value -> properties.put(JPAConfiguration.ATTACHMENT_STORAGE, value));

        return Persistence.createEntityManagerFactory("Global", properties);
    }

    @Provides
    @Singleton
    JPAConfiguration provideConfiguration(PropertiesProvider propertiesProvider) throws FileNotFoundException, ConfigurationException {
        Configuration dataSource = propertiesProvider.getConfiguration("james-database");

        Map<String, String> openjpaProperties = getKeysForPrefix(dataSource, "openjpa", false);
        Map<String, String> datasourceProperties = getKeysForPrefix(dataSource, "datasource", true);

        return JPAConfiguration.builder()
                .driverName(dataSource.getString("database.driverClassName"))
                .driverURL(dataSource.getString("database.url"))
                .testOnBorrow(dataSource.getBoolean("datasource.testOnBorrow", false))
                .validationQueryTimeoutSec(dataSource.getInteger("datasource.validationQueryTimeoutSec", null))
                .validationQuery(dataSource.getString("datasource.validationQuery", null))
                .maxConnections(dataSource.getInteger("datasource.maxTotal", null))
                .multithreaded(dataSource.getBoolean(JPAConfiguration.JPA_MULTITHREADED, true))
                .username(dataSource.getString("database.username"))
                .password(dataSource.getString("database.password"))
                .setCustomOpenjpaProperties(openjpaProperties)
                .setCustomDatasourceProperties(datasourceProperties)
                .attachmentStorage(dataSource.getBoolean(JPAConfiguration.ATTACHMENT_STORAGE, false))
                .build();
    }

    private static Map<String, String> getKeysForPrefix(Configuration dataSource, String prefix, boolean stripPrefix) {
        Iterator<String> keys = dataSource.getKeys(prefix);
        Map<String, String> properties = new HashMap<>();
        while (keys.hasNext()) {
            String key = keys.next();
            String propertyKey = stripPrefix ? key.replace(prefix + ".", "") : key;
            properties.put(propertyKey, dataSource.getString(key));
        }
        return properties;
    }
}
