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
package org.apache.james.server.core;

import java.util.regex.Pattern;

import jakarta.mail.internet.MimePart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class ContentTypeCleaner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentTypeCleaner.class);
    private static final Pattern REGEX = Pattern.compile("^[\\w\\-]+/[\\w\\-]+");
    private static final String HANDLER_CLASS_PROPERTY = "mail.mime.contenttypehandler";

    public static void initialize() {
        System.setProperty(HANDLER_CLASS_PROPERTY, ContentTypeCleaner.class.getName());
    }

    public static String cleanContentType(MimePart mimePart, String contentType) {
        if (Strings.isNullOrEmpty(contentType)) {
            return null;
        }

        if (REGEX.matcher(contentType).find()) {
            return contentType;
        }

        LOGGER.warn("Can not parse Content-Type: " + contentType);
        return null;
    }
}
