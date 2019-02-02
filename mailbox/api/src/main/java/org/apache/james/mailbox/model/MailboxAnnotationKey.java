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

package org.apache.james.mailbox.model;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.util.UnicodeSetUtils;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.ibm.icu.text.UnicodeSet;

public class MailboxAnnotationKey {

    public static final String SLASH_CHARACTER = "/";
    public static final String TWO_SLASH_CHARACTER = "//";

    private static final UnicodeSet NAME_ANNOTATION_PATTERN = UnicodeSetUtils.letterOrDigitUnicodeSet()
            .add(SLASH_CHARACTER)
            .freeze();
    public static final int MINIMUM_COMPONENTS = 2;
    public static final int MINIMUM_COMPONENTS_OF_VENDOR = 4;
    public static final int SECOND_COMPONENT_INDEX = 1;
    public static final String VENDOR_COMPONENT = "vendor";

    private final String key;

    public MailboxAnnotationKey(String key) {
        this.key = key;
        Preconditions.checkArgument(isValid(),
            "Key must start with '/' and not end with '/' and does not contain charater with hex from '\u0000' to '\u00019' or {'*', '%', two consecutive '/'} ");
    }

    private boolean isValid() {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        if (key.contains(" ")) {
            return false;
        }
        if (!key.startsWith(SLASH_CHARACTER)) {
            return false;
        }
        if (key.contains(TWO_SLASH_CHARACTER)) {
            return false;
        }
        if (key.endsWith(SLASH_CHARACTER)) {
            return false;
        }
        if (!NAME_ANNOTATION_PATTERN.containsAll(key)) {
            return false;
        }
        int componentsNo = countComponents();
        if (isVendorKey() && componentsNo < MINIMUM_COMPONENTS_OF_VENDOR) {
            return false;
        }
        if (componentsNo < MINIMUM_COMPONENTS) {
            return false;
        }
        return true;
    }

    private boolean isVendorKey() {
        String[] components = StringUtils.split(key, SLASH_CHARACTER);

        if (components.length >= MINIMUM_COMPONENTS &&
            VENDOR_COMPONENT.equalsIgnoreCase(components[SECOND_COMPONENT_INDEX])) {
            return true;
        }
        return false;
    }

    public int countComponents() {
        return StringUtils.countMatches(key, SLASH_CHARACTER);
    }

    public String asString() {
        return key.toLowerCase(Locale.US);
    }

    public boolean isParentOrIsEqual(MailboxAnnotationKey key) {
        int thatComponentsSize = key.countComponents();
        int thisComponentsSize = this.countComponents();
        return (thatComponentsSize == thisComponentsSize || thatComponentsSize == thisComponentsSize + 1)
            && isAncestorOrIsEqual(key);
    }

    public boolean isAncestorOrIsEqual(MailboxAnnotationKey key) {
        return asString().equals(key.asString()) || key.asString().startsWith(this.asString() + SLASH_CHARACTER);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MailboxAnnotationKey) {
            MailboxAnnotationKey anotherKey = (MailboxAnnotationKey)obj;
            return Objects.equal(anotherKey.asString(), asString());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(asString());
    }
}