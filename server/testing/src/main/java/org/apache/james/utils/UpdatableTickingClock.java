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

package org.apache.james.utils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.apache.commons.lang3.NotImplementedException;

public class UpdatableTickingClock extends Clock {
    private Instant currentInstant;

    public UpdatableTickingClock(Instant currentInstant) {
        this.currentInstant = currentInstant;
    }

    public void setInstant(Instant instant) {
        currentInstant = instant;
    }

    @Override
    public ZoneId getZone() {
        throw new NotImplementedException("No timezone attached to this clock");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new NotImplementedException("No timezone attached to this clock");
    }

    @Override
    public Instant instant() {
        return currentInstant;
    }

    public synchronized void tick() {
        currentInstant = currentInstant.plusMillis(1);
    }

}
