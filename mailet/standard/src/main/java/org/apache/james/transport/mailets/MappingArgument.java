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

package org.apache.james.transport.mailets;

import java.util.List;

import javax.mail.MessagingException;

import org.apache.commons.lang3.tuple.Pair;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

public class MappingArgument {

    public static String CONFIGURATION_ERROR_MESSAGE = "Invalid format, please respect key1;value1,key2;value2 format";

    public static ImmutableMap<String, String> parse(String mapping) throws MessagingException {
        Preconditions.checkArgument(mapping != null, "mapping should not be null");

        if (mapping.trim().isEmpty()) {
            return ImmutableMap.of();
        }

        return Splitter.on(',')
            .omitEmptyStrings()
            .splitToList(mapping)
            .stream()
            .map(MappingArgument::parseKeyValue)
            .collect(Guavate.toImmutableMap(Pair::getLeft, Pair::getRight));
    }

    private static Pair<String, String> parseKeyValue(String keyValue) {
        List<String> pair = Splitter.on(';')
            .trimResults()
            .splitToList(keyValue);

        if (pair.size() != 2) {
            throw new IllegalArgumentException(CONFIGURATION_ERROR_MESSAGE);
        }

        return Pair.of(pair.get(0), pair.get(1));
    }
}
