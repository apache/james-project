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
package org.apache.james.modules.server;

import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.webadmin.service.ExportService;
import org.apache.james.webadmin.service.MailboxesExportTask;
import org.apache.james.webadmin.service.MailboxesExportTaskAdditionalInformationDTO;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;

public class WebadminMailboxExportTaskSerializationModule extends AbstractModule {
    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> mailboxesExportTask(ExportService exportService) {
        return MailboxesExportTask.module(exportService);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> mailboxesExportAdditionalInformation() {
        return MailboxesExportTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }
}
