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
package org.apache.james.imapserver.netty;

import java.util.Map;

import org.apache.james.imap.api.process.ImapSession;

import io.netty.util.AttributeKey;
import reactor.core.Disposable;


/**
 * Just some constants which are used with the Netty implementation
 */
public interface NettyConstants {
    String ZLIB_DECODER = "zlibDecoder";
    String ZLIB_ENCODER = "zlibEncoder";
    String SSL_HANDLER = "sslHandler";
    String REQUEST_DECODER = "requestDecoder";
    String FRAMER = "framer";
    String TIMEOUT_HANDLER = "timeoutHandler";
    String CORE_HANDLER = "coreHandler";
    String CHUNK_WRITE_HANDLER = "chunkWriteHandler";
    String HEARTBEAT_HANDLER = "heartbeatHandler";

    AttributeKey<ImapSession> IMAP_SESSION_ATTRIBUTE_KEY = AttributeKey.valueOf("ImapSession");
    AttributeKey<Linearalizer> LINEARALIZER_ATTRIBUTE_KEY = AttributeKey.valueOf("Linearalizer");
    AttributeKey<Disposable> REQUEST_IN_FLIGHT_ATTRIBUTE_KEY = AttributeKey.valueOf("requestInFlight");
    AttributeKey<Runnable> BACKPRESSURE_CALLBACK = AttributeKey.valueOf("BACKPRESSURE_CALLBACK");
    AttributeKey<Map<String, Object>> FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY  = AttributeKey.valueOf("FrameDecoderMap");

}
