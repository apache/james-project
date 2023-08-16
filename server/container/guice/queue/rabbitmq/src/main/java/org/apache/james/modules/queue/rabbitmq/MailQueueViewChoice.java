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

package org.apache.james.modules.queue.rabbitmq;

import static org.apache.james.modules.queue.rabbitmq.RabbitMQModule.RABBITMQ_CONFIGURATION_NAME;

import java.io.FileNotFoundException;

import com.google.inject.util.Modules;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.modules.server.BrowseStartTaskModule;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.Module;

public enum MailQueueViewChoice {
    CASSANDRA,
    NONE;

    public static class ModuleChooser {
        public static Module choose(MailQueueViewChoice choice) {
            switch (choice) {
                case CASSANDRA:
                    return Modules.combine(new CassandraMailQueueViewModule(),
                        new BrowseStartTaskModule());
                case NONE:
                    return new FakeMailQueueViewModule();
                default:
                    throw new NotImplementedException();
            }
        }
    }

    public static MailQueueViewChoice parse(Configuration configuration) {
        if (configuration.getBoolean("cassandra.view.enabled", true)) {
            return CASSANDRA;
        } else {
            return NONE;
        }
    }

    public static MailQueueViewChoice parse(PropertiesProvider configuration) {
        try {
            return parse(configuration.getConfiguration(RABBITMQ_CONFIGURATION_NAME));
        } catch (FileNotFoundException e) {
            return CASSANDRA;
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}
