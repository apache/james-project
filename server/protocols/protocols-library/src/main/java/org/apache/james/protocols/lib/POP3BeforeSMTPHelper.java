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

package org.apache.james.protocols.lib;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Helper class which is used to store ipAddresses and timestamps for pop before
 * smtp support.
 */
public class POP3BeforeSMTPHelper {

    /**
     * The map in which the ipAddresses and timestamp stored
     */
    public static final Map<String, Instant> IP_MAP = Collections.synchronizedMap(new HashMap<>());

    /**
     * Default expire time in ms (60 hour)
     */
    public static final Duration EXPIRE_TIME = Duration.ofHours(60);

    /**
     * Return true if the ip is authorized to relay
     * 
     * @param ipAddress
     *            The ipAddress
     * @return true if authorized. Else false
     */
    public static boolean isAuthorized(String ipAddress) {
        return IP_MAP.containsKey(ipAddress);
    }

    /**
     * Add the ipAddress to the authorized ipAddresses
     * 
     * @param ipAddress
     *            The ipAddress
     */
    public static void addIPAddress(String ipAddress) {
        IP_MAP.put(ipAddress, Instant.now());
    }

    /**
     * Remove all ipAddress from the authorized map which are older then the
     * given time
     * 
     * @param clearTime
     *            The time in milliseconds after which an ipAddress should be
     *            handled as expired
     */
    public static void removeExpiredIP(Duration clearTime) {
        synchronized (IP_MAP) {
            Iterator<String> storedIP = IP_MAP.keySet().iterator();

            while (storedIP.hasNext()) {
                String key = storedIP.next();
                Instant storedTime = IP_MAP.get(key);

                // remove the ip from the map when it is expired
                if (Instant.now().minus(clearTime).isAfter(storedTime)) {
                    // remove the entry from the iterator first to get sure that
                    // we not get
                    // a ConcurrentModificationException
                    storedIP.remove();

                    IP_MAP.remove(key);
                }
            }
        }
    }
}
