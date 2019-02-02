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

package org.apache.james.protocols.smtp.hook;

import java.util.List;
import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class HookReturnCode {


    public enum Action {
        OK,
        DENY,
        DENYSOFT,
        DECLINED,
        NONE;

        public static List<Action> ACTIVE_ACTIONS =
            ImmutableList.of(HookReturnCode.Action.DENY, HookReturnCode.Action.DENYSOFT, HookReturnCode.Action.OK);

    }

    public enum ConnectionStatus {
        Disconnected,
        Connected
    }

    public static HookReturnCode denySoft() {
        return connected(Action.DENYSOFT);
    }

    public static HookReturnCode deny() {
        return connected(Action.DENY);
    }

    public static HookReturnCode ok() {
        return connected(Action.OK);
    }

    public static HookReturnCode declined() {
        return connected(Action.DECLINED);
    }

    public static HookReturnCode connected(Action action) {
        return new HookReturnCode(action, ConnectionStatus.Connected);
    }

    public static HookReturnCode disconnected(Action action) {
        return new HookReturnCode(action, ConnectionStatus.Disconnected);
    }

    private final Action action;
    private final ConnectionStatus connectionStatus;

    public HookReturnCode(Action action, ConnectionStatus connectionStatus) {
        this.action = action;
        this.connectionStatus = connectionStatus;
    }

    public Action getAction() {
        return action;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public boolean isDisconnected() {
        return connectionStatus == ConnectionStatus.Disconnected;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof HookReturnCode) {
            HookReturnCode that = (HookReturnCode) o;

            return Objects.equals(this.action, that.action)
                && Objects.equals(this.connectionStatus, that.connectionStatus);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(action, connectionStatus);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("action", action)
            .add("disconnection", connectionStatus)
            .toString();
    }
}
