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
package org.apache.james.modules.mailbox;

import java.util.Objects;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.mailbox.extension.PreDeletionHook;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class PreDeletionHookConfiguration {

    public static final String CLASS_NAME_CONFIGURATION_ENTRY = "class";

    public static PreDeletionHookConfiguration from(HierarchicalConfiguration configuration) throws ConfigurationException {
        Preconditions.checkNotNull(configuration);

        try {
            return forClass(configuration.getString(CLASS_NAME_CONFIGURATION_ENTRY));
        } catch (Exception e) {
            throw new ConfigurationException("Exception encountered in PreDeletionHook configuration", e);
        }
    }

    public static PreDeletionHookConfiguration forClass(Class<? extends PreDeletionHook> clazz) {
        return forClass(clazz.getName());
    }

    public static PreDeletionHookConfiguration forClass(String clazz) {
        return new PreDeletionHookConfiguration(clazz);
    }

    private final String clazz;

    private PreDeletionHookConfiguration(String clazz) {
        Preconditions.checkNotNull(clazz, "class name is mandatory");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(clazz), "class name should not be empty");

        this.clazz = clazz;
    }

    public String getClazz() {
        return clazz;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PreDeletionHookConfiguration) {
            PreDeletionHookConfiguration that = (PreDeletionHookConfiguration) o;

            return Objects.equals(this.clazz, that.clazz);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(clazz);
    }
}