# Use one or more James instances behind a proxy

It is common to run a proxy in front of a mail server such as James. The proxy receives incoming
connections and forwards the request (e.g. SMTP) to James. The proxy passes the remote address to
James, so James knows the origin ip address of the remote peer.

So far SMTP has been tested successfully, but is not limited to. Since the proxy protocol
implementation is implemented on Netty level, it can also be used for IMAP.

## Proxy

This example uses [HAProxy](https://www.haproxy.org/). Other proxy software that implement
[HAProxy's proxy protocol](https://www.haproxy.org/download/2.7/doc/proxy-protocol.txt) can also be
used (e.g. [traefik](https://traefik.io)).

It is possible to run more than one James instance and let the proxy decide which connection it
forwards to which instance (i.e. load balancing).

## SMTP

This docker example uses HAProxy exposes port 25 (SMTP)

James might take a while to start. Thus start James first, then haproxy and finally helo.
```shell
docker-compose up -d james
# wait until james is ready
docker-compose up -d haproxy

# sends a "HELO" SMTP command to haproxy that will be forwarded to james. James will answer
# including the remote ip address of the helo container.
docker-compose up helo
```

If you expose HAProxy's port 25 you can also send a HELO using telnet.

Similarly, one can test a setup mixing SSL and SMTP (with James responsible for SSL termination):

```
openssl s_client -host 127.0.0.1 -port 465
```

## IMAP

This docker example uses HAProxy exposes port 143 (SMTP)

Then interacting with HAProxy port 143:

```
telnet 127.0.0.1 143
```

Will generate the following logs:

```
james      | 02:30:48.927 [INFO ] o.a.j.i.n.ImapChannelUpstreamHandler - Connection established from 192.168.208.3
james      | 02:30:48.963 [INFO ] o.a.j.i.n.HAProxyMessageHandler - Connection from 192.168.208.1 runs through 192.168.208.3 proxy
```

Similarly, one can test a setup mixing SSL and IMAP (with James responsible for SSL termination):

```
openssl s_client -host 127.0.0.1 -port 993
```

## Limitations

* Since HAProxy's protocol does not require to know the application layer protocol,
it is not possible to load balance based on SMTP AUTH user or virtual domain. See 
HAProxy's documenation about different load balancing strategies
(e.g. round-robin, leastconn, source etc.)

* When running James behind a proxy it is currently not possible to talk directly to
the James instance (e.g. telnet). The connection will be closed and an exception is
raise in James complaining no proxy is used. Be sure to talk to the proxy with telnet
and everything is fine. This behaviour might change in future versions.