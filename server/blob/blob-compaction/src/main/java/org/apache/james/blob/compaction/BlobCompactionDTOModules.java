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

package org.apache.james.blob.compaction;

import java.time.Clock;
import java.util.function.Supplier;

import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTOModule;

public class BlobCompactionDTOModules {
    public static TaskDTOModule<BlobCompactionTask, BlobCompactionTaskDTO> taskModule(BlobCompactionAlgorithm algorithm, Clock clock) {
        return BlobCompactionTaskDTO.module(algorithm, clock);
    }

    public static TaskDTOModule<BlobCompactionTask, BlobCompactionTaskDTO> taskModule(Supplier<BlobCompactionAlgorithm> algorithmSupplier, Clock clock) {
        return BlobCompactionTaskDTO.module(algorithmSupplier, clock);
    }

    public static TaskDTOModule<InitialBlobCompactionTask, InitialBlobCompactionTaskDTO> initialCompactionTaskModule(BlobCompactionAlgorithm algorithm, Clock clock) {
        return InitialBlobCompactionTaskDTO.module(algorithm, clock);
    }

    public static TaskDTOModule<InitialBlobCompactionTask, InitialBlobCompactionTaskDTO> initialCompactionTaskModule(Supplier<BlobCompactionAlgorithm> algorithmSupplier, Clock clock) {
        return InitialBlobCompactionTaskDTO.module(algorithmSupplier, clock);
    }

    public static TaskDTOModule<GCBlobCompactionTask, GCBlobCompactionTaskDTO> gcCompactionTaskModule(BlobCompactionAlgorithm algorithm, Clock clock) {
        return GCBlobCompactionTaskDTO.module(algorithm, clock);
    }

    public static TaskDTOModule<GCBlobCompactionTask, GCBlobCompactionTaskDTO> gcCompactionTaskModule(Supplier<BlobCompactionAlgorithm> algorithmSupplier, Clock clock) {
        return GCBlobCompactionTaskDTO.module(algorithmSupplier, clock);
    }

    public static AdditionalInformationDTOModule<BlobCompactionTask.AdditionalInformation, BlobCompactionTaskAdditionalInformationDTO> additionalInformationModule() {
        return BlobCompactionTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    public static AdditionalInformationDTOModule<InitialBlobCompactionTask.AdditionalInformation, InitialBlobCompactionTaskAdditionalInformationDTO> initialAdditionalInformationModule() {
        return InitialBlobCompactionTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    public static AdditionalInformationDTOModule<GCBlobCompactionTask.AdditionalInformation, GCBlobCompactionTaskAdditionalInformationDTO> gcAdditionalInformationModule() {
        return GCBlobCompactionTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }
}
