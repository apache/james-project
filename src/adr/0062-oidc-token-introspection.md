# 62. OIDC token introspection

Date: 2022-09-13

## Status

Accepted (lazy consensus).

Implemented. 

Complements [ADR 51](0051-oidc.md).

## Context

[ADR 51](0051-oidc.md) describes work required for OIDC adoption within James.

This work enables the uses of an OIDC access token to authenticate using IMAP and SMTP.
It validates the signature of the token using cryptographic materials exposes by the 
Identity Provider server through the mean of a JWKS endpoint. Yet no effort is made to
see if the access token in question was revoked or not, which can pause a security threat.

OIDC ecosystem can support the following mechanisms to determine if an access token had been 
revoked:

 - Use of an introspection endpoint: the application asks the OIDC server to validate the token
 through an HTTP call. This result in load on the identity provider, which becomes central to the
 authentication process. This can be assimilated to a 'synchronous' mode.
 - Use of back-channel upon token revocation. The OIDC provider is then responsible to call an 
 applicative endpoint to invalidate a token. Invalidated tokens are then stored by the application
 and access token are challenged against that storage. This approach scales better yet is harder 
 to implement and can be seen as less secure (a network incident can prevent revoked token 
 propagation to applications for instance). This can be seen as an 'asynchronous' mode.
 
Also, we need to keep in mind that OIDC validation is only done upon establishing the connection in 
IMAP/SMTP (as they are connected protocols) which defers from stateless protocols like HTTP. Performance
impact of token introspection is thus lower for connected protocols.

## Decision

Allow opt-in use of an introspection endpoint to further secure IMAP/SMTP OIDC implementation.

## Consequences

Security gains for the IMAP/SMTP OIDC implementation in James.

## References

- [JIRA JAMES-3755](https://issues.apache.org/jira/browse/JAMES-3755)
- [RFC-7628](https://www.rfc-editor.org/rfc/rfc7628.html) SASL OATH mechanism for SMTP and IMAP: https://datatracker.ietf.org/doc/html/rfc7628
- [RFC-7662](https://datatracker.ietf.org/doc/html/rfc7662) OAUTH token introspection