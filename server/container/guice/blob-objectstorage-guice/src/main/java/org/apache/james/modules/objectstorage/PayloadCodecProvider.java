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

package org.apache.james.modules.objectstorage;

import java.io.FileNotFoundException;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.blob.objectstorage.PayloadCodec;
import org.apache.james.utils.PropertiesProvider;

import com.amazonaws.util.StringUtils;
import com.google.common.base.Preconditions;

public class PayloadCodecProvider implements Provider<PayloadCodec> {
    static final String OBJECTSTORAGE_PAYLOAD_CODEC = "objectstorage.payload.codec";

    private static final String OBJECTSTORAGE_CONFIGURATION_NAME = "objectstorage";

    private final Configuration configuration;

    @Inject
    public PayloadCodecProvider(PropertiesProvider propertiesProvider)
            throws ConfigurationException {
        try {
            this.configuration =
                propertiesProvider.getConfiguration(OBJECTSTORAGE_CONFIGURATION_NAME);
        } catch (FileNotFoundException e) {
            throw new ConfigurationException(OBJECTSTORAGE_CONFIGURATION_NAME + "configuration was not found");
        }
    }

    @Override
    public PayloadCodec get() {
        String codecName = configuration.getString(OBJECTSTORAGE_PAYLOAD_CODEC, null);
        Preconditions.checkArgument(!StringUtils.isNullOrEmpty(codecName),
            OBJECTSTORAGE_PAYLOAD_CODEC + " is a mandatory configuration value");
        return PayloadCodecs.valueOf(codecName).codec(configuration);
    }
}
