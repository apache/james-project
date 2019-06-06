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

package org.apache.james.util;

import java.util.concurrent.ThreadLocalRandom;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

public class Port {
    public static final int MAX_PORT_VALUE = 65535;
    public static final int PRIVILEGED_PORT_BOUND = 1024;
    private static final Range<Integer> VALID_PORT_RANGE = Range.closed(1, MAX_PORT_VALUE);

    public static Port of(int portNumber) {
        return new Port(portNumber);
    }

    public static int generateValidUnprivilegedPort() {
        return ThreadLocalRandom.current().nextInt(Port.MAX_PORT_VALUE - PRIVILEGED_PORT_BOUND) + PRIVILEGED_PORT_BOUND;
    }

    public static void assertValid(int port) {
        Preconditions.checkArgument(isValid(port), "Port should be between 1 and 65535");
    }

    public static boolean isValid(int port) {
        return VALID_PORT_RANGE.contains(port);
    }

    private final int value;

    public Port(int value) {
        validate(value);
        this.value = value;
    }

    protected void validate(int port) {
        assertValid(port);
    }

    public int getValue() {
        return value;
    }
}
