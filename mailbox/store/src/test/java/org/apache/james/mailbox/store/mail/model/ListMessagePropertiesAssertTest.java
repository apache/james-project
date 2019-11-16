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

package org.apache.james.mailbox.store.mail.model;

import static org.apache.james.mailbox.store.mail.model.ListMessagePropertiesAssert.assertProperties;

import java.util.List;

import org.apache.james.mailbox.store.mail.model.impl.SimpleProperty;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ListMessagePropertiesAssertTest {
    private static final String OTHER_VALUE = "US-ASCII";
    private static final String OTHER_LOCAL_NAME = StandardNames.MIME_CONTENT_TYPE_PARAMETER_CHARSET_NAME;
    private static final String OTHER_NAMESPACE = StandardNames.MIME_CONTENT_TYPE_PARAMETER_SPACE;
    private static final String VALUE = "7bit";
    private static final String LOCAL_NAME = StandardNames.MIME_CONTENT_TRANSFER_ENCODING_NAME;
    private static final String NAMESPACE = StandardNames.NAMESPACE_RFC_2045;

    private static final Property PROPERTY1 = new SimpleProperty(NAMESPACE, LOCAL_NAME, VALUE);
    private static final Property PROPERTY2 = new SimpleProperty(OTHER_NAMESPACE, OTHER_LOCAL_NAME, OTHER_VALUE);
    
    private List<Property> actual;
    
    @Before
    public void setUp() {
        actual = ImmutableList.of(PROPERTY1, PROPERTY2);
    }
    
    @Test
    public void containsOnlyShouldWork() {
        assertProperties(actual).containsOnly(ImmutableList.of(createProperty(NAMESPACE, LOCAL_NAME, VALUE),
            createProperty(OTHER_NAMESPACE, OTHER_LOCAL_NAME, OTHER_VALUE)));
    }
    
    @Test(expected = AssertionError.class)
    public void containsOnlyShouldFailWhenNotEnoughElement() {
        assertProperties(actual).containsOnly(ImmutableList.of(createProperty(NAMESPACE, LOCAL_NAME, VALUE)));
    }

    @Test(expected = AssertionError.class)
    public void containsOnlyShouldFailWhenNamespaceMismatch() {
        assertProperties(actual).containsOnly(ImmutableList.of(createProperty(NAMESPACE, LOCAL_NAME, VALUE),
            createProperty(OTHER_NAMESPACE, LOCAL_NAME, VALUE)));
    }

    @Test(expected = AssertionError.class)
    public void containsOnlyShouldFailWhenNameMismatch() {
        assertProperties(actual).containsOnly(ImmutableList.of(createProperty(NAMESPACE, LOCAL_NAME, VALUE),
            createProperty(NAMESPACE, OTHER_LOCAL_NAME, VALUE)));
    }

    @Test(expected = AssertionError.class)
    public void containsOnlyShouldFailWhenValueMismatch() {
        assertProperties(actual).containsOnly(ImmutableList.of(createProperty(NAMESPACE, LOCAL_NAME, VALUE),
            createProperty(NAMESPACE, LOCAL_NAME, OTHER_VALUE)));
    }

    private Property createProperty(final String namespace, final String name, final String value) {
        return new Property() {
            @Override
            public String getValue() {
                return value;
            }
            
            @Override
            public String getNamespace() {
                return namespace;
            }
            
            @Override
            public String getLocalName() {
                return name;
            }
        };
    }
}
