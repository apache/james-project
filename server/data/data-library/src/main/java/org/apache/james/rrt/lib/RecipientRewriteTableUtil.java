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
package org.apache.james.rrt.lib;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;

import org.apache.james.core.Domain;

/**
 * This helper class contains methods for the RecipientRewriteTable implementations
 */
public class RecipientRewriteTableUtil {

    private RecipientRewriteTableUtil() {
    }

    /**
     * Returns the real recipient given a virtual username and domain.
     *
     * @param user
     *            the virtual user
     * @param domain
     *            the virtual domain
     * @return the real recipient address, or <code>null</code> if no mapping
     *         exists
     */
    public static String getTargetString(String user, Domain domain, Map<MappingSource, String> mappings) {
        return Optional.ofNullable(mappings.get(MappingSource.fromUser(user, domain)))
            .or(() -> Optional.ofNullable(mappings.get(MappingSource.fromDomain(domain))))
            .orElse(null);
    }

    /**
     * Returns a Map which contains the mappings
     * 
     * @param mapping
     *            A String which contains a list of mappings
     * @return Map which contains the mappings
     */
    public static Map<MappingSource, String> getXMLMappings(String mapping) {
        Map<MappingSource, String> mappings = new HashMap<>();
        StringTokenizer tokenizer = new StringTokenizer(mapping, ",");
        while (tokenizer.hasMoreTokens()) {
            String mappingItem = tokenizer.nextToken();
            int index = mappingItem.indexOf('=');
            String virtual = mappingItem.substring(0, index).trim().toLowerCase(Locale.US);
            String real = mappingItem.substring(index + 1).trim().toLowerCase(Locale.US);
            mappings.put(MappingSource.parse(virtual), real);
        }
        return mappings;
    }

}
