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

package org.apache.james.transport.mailets.remote.delivery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

/**
 * <p>
 * It is used by RemoteDelivery in order to make possible to bind the client
 * socket to a specific ip address.
 * </p>
 * <p>
 * This is not a nice solution because the ip address must be shared by all
 * RemoteDelivery instances. It would be better to modify JavaMail (current
 * version 1.3) to support a corresonding property, e.g. mail.smtp.bindAdress.
 * </p>
 * <p>
 * This used to not extend javax.net.SocketFactory descendant, because
 * <ol>
 * <li>
 * it was not necessary because JavaMail 1.2 uses reflection when accessing this
 * class;</li>
 * <li>
 * it was not desirable because it would require java 1.4.</li>
 * </ol>
 * </p>
 * <p>
 * But since James 2.3.0a1:
 * <ol>
 * <li>we require Java 1.4 so the dependency on SocketFactory is not really an
 * issue;</li>
 * <li>Javamail 1.4 cast the object returned by getDefault to SocketFactory and
 * fails to create the socket if we don't extend SocketFactory.</li>
 * </ol>
 * </p>
 * <p>
 * <strong>Note</strong>: Javamail 1.4 should correctly support
 * mail.smtp.localaddr so we could probably get rid of this class and simply add
 * that property to the Session.
 * </p>
 */
public class RemoteDeliverySocketFactory extends SocketFactory {

    /**
     * @param addr
     *            the ip address or host name the delivery socket will bind to
     */
    public static void setBindAdress(String addr) throws UnknownHostException {
        if (addr == null) {
            bindAddress = null;
        } else {
            bindAddress = InetAddress.getByName(addr);
        }
    }

    /**
     * the same as the similarly named javax.net.SocketFactory operation.
     */
    public static SocketFactory getDefault() {
        return new RemoteDeliverySocketFactory();
    }

    /**
     * the same as the similarly named javax.net.SocketFactory operation. Just
     * to be safe, it is not used by JavaMail 1.3. This is the only method used
     * by JavaMail 1.4.
     */
    @Override
    public Socket createSocket() throws IOException {
        Socket s = new Socket();
        s.bind(new InetSocketAddress(bindAddress, 0));
        return s;
    }

    /**
     * the same as the similarly named javax.net.SocketFactory operation. This
     * is the one which is used by JavaMail 1.3. This is not used by JavaMail
     * 1.4.
     */
    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return new Socket(host, port, bindAddress, 0);
    }

    /**
     * the same as the similarly named javax.net.SocketFactory operation. Just
     * to be safe, it is not used by JavaMail 1.3. This is not used by JavaMail
     * 1.4.
     */
    @Override
    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort) throws IOException {
        return new Socket(host, port, clientHost == null ? bindAddress : clientHost, clientPort);
    }

    /**
     * the same as the similarly named javax.net.SocketFactory operation. Just
     * to be safe, it is not used by JavaMail 1.3. This is not used by JavaMail
     * 1.4.
     */
    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return new Socket(host, port, bindAddress, 0);
    }

    /**
     * the same as the similarly named javax.net.SocketFactory operation. Just
     * to be safe, it is not used by JavaMail 1.3. This is not used by JavaMail
     * 1.4.
     */
    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress clientAddress, int clientPort) throws IOException {
        return new Socket(address, port, clientAddress == null ? bindAddress : clientAddress, clientPort);
    }

    /**
     * it should be set by setBindAdress(). Null means the socket is bind to the
     * default address.
     */
    private static InetAddress bindAddress;
}
