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
package org.apache.james.modules.vault;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.vault.blob.BlobStoreVaultGarbageCollectionTask;
import org.apache.james.vault.blob.BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO;
import org.apache.james.vault.blob.BlobStoreVaultGarbageCollectionTaskDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultDeleteTask;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultDeleteTaskAdditionalInformationDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultDeleteTaskDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultExportTaskAdditionalInformationDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultExportTaskDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTaskAdditionalInformationDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTaskDTO;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;

public class VaultTaskSerializationModule extends AbstractModule {
    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> blobStoreVaultGarbageCollectionTask(BlobStoreVaultGarbageCollectionTask.Factory factory) {
        return BlobStoreVaultGarbageCollectionTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> deletedMessagesVaultDeleteTask(DeletedMessagesVaultDeleteTask.Factory factory) {
        return DeletedMessagesVaultDeleteTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> deletedMessagesVaultExportTask(DeletedMessagesVaultExportTaskDTO.Factory factory) {
        return DeletedMessagesVaultExportTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> deletedMessagesVaultRestoreTask(DeletedMessagesVaultRestoreTaskDTO.Factory factory) {
        return DeletedMessagesVaultRestoreTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> blobStoreVaultGarbageCollectionAdditionalInformation() {
        return BlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> deletedMessagesVaultDeleteAdditionalInformation(MessageId.Factory factory) {
        return DeletedMessagesVaultDeleteTaskAdditionalInformationDTO.serializationModule(factory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> deletedMessagesVaultExportAdditionalInformation() {
        return DeletedMessagesVaultExportTaskAdditionalInformationDTO.MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> deletedMessagesVaultRestoreAdditionalInformation() {
        return DeletedMessagesVaultRestoreTaskAdditionalInformationDTO.MODULE;
    }
}
