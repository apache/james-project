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

package org.apache.james.protocols.netty;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jboss.netty.channel.Channel;
import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;

public class SslEngineUtilTest {

    //remote connection fields
    private String remoteAddress = "1.1.1.1";
    private int remotePort = 1;
    //local connection fields
    private String localAddress = "2.2.2.2";
    private int localPort = 2;


    @Test
    public void testDefaultSslEngineUtilMode()  throws Exception {
        //since channel is null, will use the default implementation
        SSLEngine engine = SslEngineUtil.INSTANCE.generateSslEngine(null, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        Channel channel = mock(Channel.class);

        //since SslEngineUtil.INSTANCE will use Mode NONE by default, will use the default implementation
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);
    }

    @Test
    public void testSslEngineUtilMode_null()  throws Exception {
        SslEngineUtil.INSTANCE.setSslEngineUtilMode(null);
        //since channel is null, will use the default implementation
        SSLEngine engine = SslEngineUtil.INSTANCE.generateSslEngine(null, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        Channel channel = mock(Channel.class);

        //since SslEngineUtil.INSTANCE will use Mode NONE by default, will use the default implementation
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);
    }

    @Test
    public void testSslEngineUtilMode_NONE()  throws Exception {
        SslEngineUtil.INSTANCE.setSslEngineUtilMode(SslEngineUtil.SslEngineUtilMode.NONE);
        //since channel is null, will use the default implementation
        SSLEngine engine = SslEngineUtil.INSTANCE.generateSslEngine(null, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        Channel channel = mock(Channel.class);
        //since SslEngineUtil.INSTANCE will use Mode NONE by default, will use the default implementation
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);
    }

    @Test
    public void testSslEngineUtilMode_REMOTE_ONLY()  throws Exception {
        SslEngineUtil.INSTANCE.setSslEngineUtilMode(SslEngineUtil.SslEngineUtilMode.REMOTE_ONLY);
        //channel is null, should return default values
        SSLEngine engine = SslEngineUtil.INSTANCE.generateSslEngine(null, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        //channel is empty, should return default values
        Channel channel = mock(Channel.class);
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        //set up with local, should return default values
        when(channel.isBound()).thenReturn(true);
        when(channel.getLocalAddress()).thenReturn(new InetSocketAddress(localAddress, localPort));
        //since channel is connected and has remote address, build SslEngine with those as peer
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        //set up with remote, should return remote values
        when(channel.isConnected()).thenReturn(true);
        when(channel.getRemoteAddress()).thenReturn(new InetSocketAddress(remoteAddress, remotePort));
        //since channel is connected and has remote address, build SslEngine with those as peer
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost().equals(remoteAddress));
        Assert.assertTrue(engine.getPeerPort() == remotePort);
    }

    @Test
    public void testSslEngineUtilMode_LOCAL_ONLY()  throws Exception {
        SslEngineUtil.INSTANCE.setSslEngineUtilMode(SslEngineUtil.SslEngineUtilMode.LOCAL_ONLY);
        //channel is null, should return default values
        SSLEngine engine = SslEngineUtil.INSTANCE.generateSslEngine(null, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        //channel is empty, should return default values
        Channel channel = mock(Channel.class);
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        //set up with remote, should return default values
        when(channel.isConnected()).thenReturn(true);
        when(channel.getRemoteAddress()).thenReturn(new InetSocketAddress(remoteAddress, remotePort));
        //since channel is connected and has remote address, build SslEngine with those as peer
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        //set up with local, should return local values
        when(channel.isBound()).thenReturn(true);
        when(channel.getLocalAddress()).thenReturn(new InetSocketAddress(localAddress, localPort));
        //since channel is connected and has remote address, build SslEngine with those as peer
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost().equals(localAddress));
        Assert.assertTrue(engine.getPeerPort() == localPort);
    }

    @Test
    public void testSslEngineUtilMode_LOCAL_REMOTE()  throws Exception {
        SslEngineUtil.INSTANCE.setSslEngineUtilMode(SslEngineUtil.SslEngineUtilMode.LOCAL_REMOTE);
        //channel is null, should return default values
        SSLEngine engine = SslEngineUtil.INSTANCE.generateSslEngine(null, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        //channel is empty, should return default values
        Channel channel = mock(Channel.class);
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        //set up with remote, should return remote values
        when(channel.isConnected()).thenReturn(true);
        when(channel.getRemoteAddress()).thenReturn(new InetSocketAddress(remoteAddress, remotePort));
        //since channel is connected and has remote address, build SslEngine with those as peer
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost().equals(remoteAddress));
        Assert.assertTrue(engine.getPeerPort() == remotePort);

        //set up with local, should return local values
        when(channel.isBound()).thenReturn(true);
        when(channel.getLocalAddress()).thenReturn(new InetSocketAddress(localAddress, localPort));
        //since channel is connected and has remote address, build SslEngine with those as peer
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost().equals(localAddress));
        Assert.assertTrue(engine.getPeerPort() == localPort);
    }

    @Test
    public void testSslEngineUtilMode_REMOTE_LOCAL()  throws Exception {
        SslEngineUtil.INSTANCE.setSslEngineUtilMode(SslEngineUtil.SslEngineUtilMode.REMOTE_LOCAL);
        //channel is null, should return default values
        SSLEngine engine = SslEngineUtil.INSTANCE.generateSslEngine(null, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        //channel is empty, should return default values
        Channel channel = mock(Channel.class);
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost() == null);
        Assert.assertTrue(engine.getPeerPort() == -1);

        //set up with local, should return local values
        when(channel.isBound()).thenReturn(true);
        when(channel.getLocalAddress()).thenReturn(new InetSocketAddress(localAddress, localPort));
        //since channel is connected and has remote address, build SslEngine with those as peer
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost().equals(localAddress));
        Assert.assertTrue(engine.getPeerPort() == localPort);

        //set up with remote, should return remote values
        when(channel.isConnected()).thenReturn(true);
        when(channel.getRemoteAddress()).thenReturn(new InetSocketAddress(remoteAddress, remotePort));
        //since channel is connected and has remote address, build SslEngine with those as peer
        engine = SslEngineUtil.INSTANCE.generateSslEngine(channel, SSLContext.getDefault());
        Assert.assertTrue(engine.getPeerHost().equals(remoteAddress));
        Assert.assertTrue(engine.getPeerPort() == remotePort);
    }
}
