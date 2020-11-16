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

import static org.apache.james.modules.mailbox.ConfigurationComponent.NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.FakePropertiesProvider;
import org.apache.james.blob.export.file.LocalFileBlobExportMechanism;
import org.apache.james.linshare.LinshareBlobExportMechanism;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.inject.Provider;

class BlobExportMechanismModuleTest {

    private static LocalFileBlobExportMechanism LOCAL_FILE_EXPORT = mock(LocalFileBlobExportMechanism.class);
    private static LinshareBlobExportMechanism LINSHARE_EXPORT = mock(LinshareBlobExportMechanism.class);
    private static Provider<LocalFileBlobExportMechanism> LOCAL_FILE_EXPORT_PROVIDER = () -> LOCAL_FILE_EXPORT;
    private static Provider<LinshareBlobExportMechanism> LINSHARE_FILE_EXPORT_PROVIDER = () -> LINSHARE_EXPORT;

    private BlobExportMechanismModule module;

    @BeforeEach
    void beforeEach() {
        module = new BlobExportMechanismModule();
    }

    @Test
    void provideChoiceShouldReturnLocalFileWhenConfigurationNotFound() throws Exception {
        FakePropertiesProvider noConfigurationFile = FakePropertiesProvider.builder().build();

        assertThat(module.provideChoice(noConfigurationFile))
            .isEqualTo(BlobExportImplChoice.LOCAL_FILE);
    }

    @Test
    void provideChoiceShouldReturnLocalFileWhenLocalFile() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("blob.export.implementation", "localFile");

        FakePropertiesProvider noConfigurationFile = FakePropertiesProvider.builder()
            .register(NAME, configuration)
            .build();

        assertThat(module.provideChoice(noConfigurationFile))
            .isEqualTo(BlobExportImplChoice.LOCAL_FILE);
    }

    @Test
    void provideChoiceShouldThrowWhenConfigurationIsUnknown() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("blob.export.implementation", "unknown");

        FakePropertiesProvider noConfigurationFile = FakePropertiesProvider.builder()
            .register(NAME, configuration)
            .build();

        assertThatThrownBy(() -> module.provideChoice(noConfigurationFile))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void provideChoiceShouldReturnDefaultWhenConfigurationIsMissing() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        FakePropertiesProvider noConfigurationFile = FakePropertiesProvider.builder()
            .register(NAME, configuration)
            .build();

        assertThat(module.provideChoice(noConfigurationFile))
            .isEqualTo(BlobExportImplChoice.LOCAL_FILE);
    }

    @Test
    void provideMechanismShouldProvideFileExportWhenPassingLocalFileChoice() {
        assertThat(module.provideMechanism(BlobExportImplChoice.LOCAL_FILE, LOCAL_FILE_EXPORT_PROVIDER, LINSHARE_FILE_EXPORT_PROVIDER))
            .isEqualTo(LOCAL_FILE_EXPORT);
    }

    @Test
    void provideMechanismShouldProvideLinshareExportWhenPassingLinshareChoice() {
        assertThat(module.provideMechanism(BlobExportImplChoice.LINSHARE, LOCAL_FILE_EXPORT_PROVIDER, LINSHARE_FILE_EXPORT_PROVIDER))
            .isEqualTo(LINSHARE_EXPORT);
    }
}