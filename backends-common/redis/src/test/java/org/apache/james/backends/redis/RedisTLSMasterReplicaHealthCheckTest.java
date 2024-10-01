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

package org.apache.james.backends.redis;

import org.apache.james.backends.redis.RedisMasterReplicaExtension.RedisMasterReplicaContainer;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class RedisTLSMasterReplicaHealthCheckTest extends RedisHealthCheckTest {
    @RegisterExtension
    private static final RedisMasterReplicaExtension redisMasterReplicaExtension = new RedisMasterReplicaExtension(true);

    private RedisHealthCheck redisHealthCheck;
    private RedisMasterReplicaContainer redisMasterReplicaContainer;

    @BeforeEach
    void setup() {
        redisHealthCheck = new RedisHealthCheck(redisMasterReplicaExtension.getRedisMasterReplicaContainer().getRedisConfiguration(), new RedisClientFactory(FileSystemImpl.forTesting()));
        redisMasterReplicaContainer = redisMasterReplicaExtension.getRedisMasterReplicaContainer();
    }

    @AfterEach
    void afterEach() {
        redisMasterReplicaContainer.unPauseOne();
    }

    @Override
    public RedisHealthCheck getRedisHealthCheck() {
        return redisHealthCheck;
    }

    @Override
    public void pauseRedis() {
        redisMasterReplicaContainer.pauseOne();
    }

    @Override
    public void unpauseRedis() {
        redisMasterReplicaContainer.unPauseOne();
    }
}
