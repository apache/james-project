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

package org.apache.james.imapserver.netty;

import org.apache.commons.configuration2.Configuration;

import io.netty.handler.traffic.ChannelTrafficShapingHandler;

public record TrafficShapingConfiguration(long writeLimit, long readLimit, long checkInterval, long maxTime) {
    static TrafficShapingConfiguration from(Configuration configuration) {
        return new TrafficShapingConfiguration(
            configuration.getLong("writeTrafficPerSecond", 0),
            configuration.getLong("readTrafficPerSecond", 0),
            configuration.getLong("checkInterval", 30),
            configuration.getLong("maxDelays", 30));
    }

    public ChannelTrafficShapingHandler newHandler() {
        return new ChannelTrafficShapingHandler(writeLimit, readLimit, checkInterval, maxTime);
    }
}
