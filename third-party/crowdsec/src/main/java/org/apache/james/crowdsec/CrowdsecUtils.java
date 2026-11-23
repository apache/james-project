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

package org.apache.james.crowdsec;

import java.net.InetAddress;

import org.apache.james.crowdsec.model.CrowdsecDecision;

import com.google.common.net.InetAddresses;

public class CrowdsecUtils {
    public static boolean isBanned(CrowdsecDecision decision, String ip) {
        try {
            InetAddress address = InetAddresses.forString(ip);
            if (decision.getScope().equals("Ip")) {
                return InetAddresses.forString(decision.getValue()).equals(address);
            }
            return decision.getScope().equals("Range") && belongsToNetwork(decision.getValue(), address);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean belongsToNetwork(String range, InetAddress address) {
        int slash = range.indexOf('/');
        if (slash < 0) {
            return false;
        }
        byte[] network = InetAddresses.forString(range.substring(0, slash)).getAddress();
        byte[] ip = address.getAddress();
        int prefixLength = Integer.parseInt(range.substring(slash + 1));
        if (network.length != ip.length || prefixLength < 0 || prefixLength > ip.length * Byte.SIZE) {
            return false;
        }
        for (int bit = 0; bit < prefixLength; bit++) {
            int mask = 0x80 >> (bit % Byte.SIZE);
            if ((network[bit / Byte.SIZE] & mask) != (ip[bit / Byte.SIZE] & mask)) {
                return false;
            }
        }
        return true;
    }
}
