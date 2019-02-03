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
import java.util.List;

import javax.inject.Singleton;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.backends.jpa.JPAConstants;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.base.Joiner;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class JPAEntityManagerModule extends AbstractModule {

    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    public EntityManagerFactory provideEntityManagerFactory(JPAConfiguration jpaConfiguration) {
        HashMap<String, String> properties = new HashMap<>();
        
        properties.put("openjpa.ConnectionDriverName", jpaConfiguration.getDriverName());
        properties.put("openjpa.ConnectionURL", jpaConfiguration.getDriverURL());

        List<String> connectionFactoryProperties = new ArrayList<>();
        connectionFactoryProperties.add("TestOnBorrow=" + jpaConfiguration.isTestOnBorrow());
        if (jpaConfiguration.getValidationQueryTimeoutSec() > 0) {
            connectionFactoryProperties.add("ValidationTimeout=" + jpaConfiguration.getValidationQueryTimeoutSec() * 1000);
        }
        if (jpaConfiguration.getValidationQuery() != null) {
            connectionFactoryProperties.add("ValidationSQL='" + jpaConfiguration.getValidationQuery() + "'");
        }
        properties.put("openjpa.ConnectionFactoryProperties", Joiner.on(", ").join(connectionFactoryProperties));

        return Persistence.createEntityManagerFactory("Global", properties);
    }

    @Provides
    @Singleton
    JPAConfiguration provideConfiguration(PropertiesProvider propertiesProvider) throws FileNotFoundException, ConfigurationException {
        Configuration dataSource = propertiesProvider.getConfiguration("james-database");
        return JPAConfiguration.builder()
                .driverName(dataSource.getString("database.driverClassName"))
                .driverURL(dataSource.getString("database.url"))
                .testOnBorrow(dataSource.getBoolean("datasource.testOnBorrow", false))
                .validationQueryTimeoutSec(dataSource.getInt("datasource.validationQueryTimeoutSec", JPAConstants.VALIDATION_NO_TIMEOUT))
                .validationQuery(dataSource.getString("datasource.validationQuery", null))
                .build();
    }
}
