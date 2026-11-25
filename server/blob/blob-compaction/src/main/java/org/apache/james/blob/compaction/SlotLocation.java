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

import com.google.common.base.Preconditions;

/** Position of a slot within a chunk, in bytes. */
record SlotLocation(long offset, long length) {
    SlotLocation {
        Preconditions.checkArgument(offset >= ChunkFormat.HEADER_SIZE, "Slot offset %s precedes the chunk header", offset);
        Preconditions.checkArgument(length >= ChunkFormat.MIN_SLOT_SIZE, "Slot length %s is below the minimal slot size", length);
        Preconditions.checkArgument(length <= Long.MAX_VALUE - offset, "Slot end overflows");
    }

    long end() {
        return offset + length;
    }

    long lastByte() {
        return end() - 1;
    }
}
