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

package org.apache.james.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class BlobExportImplChoiceTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(BlobExportImplChoice.class)
            .verify();
    }

    @Test
    void fromShouldReturnDefaultWhenImplIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThat(BlobExportImplChoice.from(configuration))
            .isEmpty();
    }

    @Test
    void fromShouldThrowWhenImplIsNotInAvailableList() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("blob.export.implementation", "unknown");

        assertThatThrownBy(() -> BlobExportImplChoice.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldReturnLocalFileImplWhenPassingLocalFileImplConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("blob.export.implementation", "localFile");

        assertThat(BlobExportImplChoice.from(configuration))
            .contains(BlobExportImplChoice.LOCAL_FILE);
    }

    @Test
    void fromShouldThrowWhenCaseInsensitive() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("blob.export.implementation", "localFILE");

        assertThatThrownBy(() -> BlobExportImplChoice.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldIgnoreBlankSpacesBeforeAndAfter() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("blob.export.implementation", "  localFile   ");

        assertThat(BlobExportImplChoice.from(configuration))
            .contains(BlobExportImplChoice.LOCAL_FILE);
    }
}