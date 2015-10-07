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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Helper class which is used to store ipAddresses and timestamps for pop before
 * smtp support.
 */
public class POP3BeforeSMTPHelper {

    private POP3BeforeSMTPHelper() {
    }

    /**
     * The map in which the ipAddresses and timestamp stored
     */
    public static final Map<String, Long> ipMap = Collections.synchronizedMap(new HashMap<String, Long>());

    /**
     * Default expire time in ms (1 hour)
     */
    public static final long EXPIRE_TIME = 216000000;

    /**
     * Return true if the ip is authorized to relay
     * 
     * @param ipAddress
     *            The ipAddress
     * @return true if authorized. Else false
     */
    public static boolean isAuthorized(String ipAddress) {
        return ipMap.containsKey(ipAddress);
    }

    /**
     * Add the ipAddress to the authorized ipAddresses
     * 
     * @param ipAddress
     *            The ipAddress
     */
    public static void addIPAddress(String ipAddress) {
        ipMap.put(ipAddress, System.currentTimeMillis());
    }

    /**
     * @see #removeExpiredIP(long)
     */
    public static void removeExpiredIP() {
        removeExpiredIP(EXPIRE_TIME);
    }

    /**
     * Remove all ipAddress from the authorized map which are older then the
     * given time
     * 
     * @param clearTime
     *            The time in milliseconds after which an ipAddress should be
     *            handled as expired
     */
    public static void removeExpiredIP(long clearTime) {
        synchronized (ipMap) {
            Iterator<String> storedIP = ipMap.keySet().iterator();
            long currTime = System.currentTimeMillis();

            while (storedIP.hasNext()) {
                String key = storedIP.next();
                long storedTime = ipMap.get(key);

                // remove the ip from the map when it is expired
                if ((currTime - clearTime) > storedTime) {
                    // remove the entry from the iterator first to get sure that
                    // we not get
                    // a ConcurrentModificationException
                    storedIP.remove();

                    ipMap.remove(key);
                }
            }
        }
    }

    /**
     * Remove all ipAddresses from the authorized map
     */
    public static void clearIP() {
        ipMap.clear();
    }
}
