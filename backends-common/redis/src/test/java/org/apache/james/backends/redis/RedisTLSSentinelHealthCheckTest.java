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

import org.apache.james.backends.redis.RedisSentinelExtension.RedisSentinelCluster;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class RedisTLSSentinelHealthCheckTest extends RedisHealthCheckTest {
    @RegisterExtension
    private static final RedisSentinelExtension redisSentinelExtension = new RedisSentinelExtension(true);

    private RedisHealthCheck redisHealthCheck;
    private RedisSentinelCluster redisSentinelCluster;

    @BeforeEach
    public void setup() {
        redisHealthCheck = new RedisHealthCheck(redisSentinelExtension.getRedisSentinelCluster().redisSentinelContainerList().getRedisConfiguration(), new RedisClientFactory(FileSystemImpl.forTesting()), new RedisConnectionFactory());
        redisSentinelCluster = redisSentinelExtension.getRedisSentinelCluster();
    }

    @AfterEach
    public void afterEach() {
        redisSentinelCluster.redisMasterReplicaContainerList().unPauseMasterNode();
    }

    @Override
    public RedisHealthCheck getRedisHealthCheck() {
        return redisHealthCheck;
    }

    @Override
    public void pauseRedis() {
        redisSentinelCluster.redisMasterReplicaContainerList().pauseMasterNode();
    }

    @Override
    public void unpauseRedis() {
        redisSentinelCluster.redisMasterReplicaContainerList().unPauseMasterNode();
    }
}
