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

package org.apache.james.webadmin.integration.rabbitmq;

import org.apache.james.CassandraRabbitMQAwsS3JmapTestRule;
import org.apache.james.DockerCassandraRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jwt.JwtConfiguration;
import org.apache.james.webadmin.authentication.AuthenticationFilter;
import org.apache.james.webadmin.authentication.JwtFilter;
import org.apache.james.webadmin.integration.JwtFilterIntegrationTest;
import org.junit.Rule;

public class RabbitMQJwtFilterIntegrationTest extends JwtFilterIntegrationTest {

    @Rule
    public DockerCassandraRule cassandra = new DockerCassandraRule();
    
    @Rule
    public CassandraRabbitMQAwsS3JmapTestRule jamesTestRule = CassandraRabbitMQAwsS3JmapTestRule.defaultTestRule();

    @Override
    protected GuiceJamesServer createJamesServer(JwtConfiguration jwtConfiguration) throws Exception {
        return jamesTestRule.jmapServer(cassandra.getModule())
            .overrideWith(binder -> binder.bind(AuthenticationFilter.class).to(JwtFilter.class),
                binder -> binder.bind(JwtConfiguration.class).toInstance(jwtConfiguration));
    }
}
