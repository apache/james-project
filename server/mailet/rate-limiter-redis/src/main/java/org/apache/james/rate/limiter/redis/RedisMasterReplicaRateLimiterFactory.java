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

package org.apache.james.rate.limiter.redis;

import java.util.List;
import java.util.Set;

import es.moki.ratelimitj.core.limiter.request.AbstractRequestRateLimiterFactory;
import es.moki.ratelimitj.core.limiter.request.ReactiveRequestRateLimiter;
import es.moki.ratelimitj.core.limiter.request.RequestLimitRule;
import es.moki.ratelimitj.core.limiter.request.RequestRateLimiter;
import es.moki.ratelimitj.redis.request.RedisSlidingWindowRequestRateLimiter;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.masterreplica.MasterReplica;
import io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection;

public class RedisMasterReplicaRateLimiterFactory extends AbstractRequestRateLimiterFactory<RedisSlidingWindowRequestRateLimiter> {
    private final RedisClient client;
    private final List<RedisURI> redisURIs;
    private final ReadFrom readFrom;
    private StatefulRedisMasterReplicaConnection<String, String> connection;

    public RedisMasterReplicaRateLimiterFactory(RedisClient client, List<RedisURI> redisURIs, ReadFrom readFrom) {
        this.client = client;
        this.redisURIs = redisURIs;
        this.readFrom = readFrom;
    }

    public RedisMasterReplicaRateLimiterFactory(RedisClient client, List<RedisURI> redisURIs) {
        this(client, redisURIs, ReadFrom.MASTER);
    }

    @Override
    protected RedisSlidingWindowRequestRateLimiter create(Set<RequestLimitRule> rules) {
        return new RedisSlidingWindowRequestRateLimiter(this.getConnection().reactive(), this.getConnection().reactive(), rules);
    }

    @Override
    public RequestRateLimiter getInstance(Set<RequestLimitRule> rules) {
        return this.lookupInstance(rules);
    }

    @Override
    public ReactiveRequestRateLimiter getInstanceReactive(Set<RequestLimitRule> rules) {
        return this.lookupInstance(rules);
    }

    @Override
    public void close() {
        this.client.shutdown();
    }

    private StatefulRedisMasterReplicaConnection<String, String> getConnection() {
        if (this.connection == null) {
            this.connection = MasterReplica.connect(this.client, StringCodec.UTF8, redisURIs);
            this.connection.setReadFrom(readFrom);
        }

        return this.connection;
    }
}
