# 51. Integrate James with OIDC

Date: 2021-12-06

## Status

Accepted (lazy consensus).

Implemented.

Complemented by [ADR-62](0062-oidc-token-introspection.md).

## Context

Authentication is a critical item regarding security. The overall internet community have been refining authentication
standards in order to improve overall safety. We can mention here 
[OIDC standard](https://openid.net/specs/openid-connect-core-1_0.html), based on OAUTH, that 
[RFC-7628](https://datatracker.ietf.org/doc/html/rfc7628) allows applying on IMAP and SMTP.

Regarding HTTP applications (including JMAP clients) the industry commonly requires "Single Sign On" and "Single Log 
Out" features.

Nowadays, James only supports plain auth for IMAP, SMTP. In JMAP, we do support HTTP basic authentication, and a set of
custom authentication methods, one being based on custom JWT. None matches the constraints mentioned above.

We believe having more control on authentication methods, and the option to choose state-of-the-art authentication
methods would ultimately increase the overall security of the James server and ease adoption.

## Decision

Provide OIDC integration for the following email protocols: IMAP, SMTP, JMAP.

Regarding JMAP, we propose ourselves to make authentication methods extensible and configurable. We will provide a 
simple authentication method blindly following the value of the `X-User` header. This would allow us to configure an 
API gateway in front of James and delegate it the OIDC management. 

Regarding IMAP and SMTP we will implement RFC-7628 within James. We will validate the validity of the Authorization 
Bearer token against the public exposed by the JWKS endpoint. 

We will document such a setup, and provide a working example under the form of a docker-compose using 
[Keycloack](https://www.keycloak.org/) as an OIDC provider and [Krakend](https://www.krakend.io/) as an API gateway.

We will allow configuring and un-configuring any of the configuration mechanism. For instance, it will be possible to 
operate an OIDC-only James server.

## Consequences

The `X-User` authentication mechanism should be enabled with care as it allows the API gateway to bypass authentication
with the James server. Given such an OIDC setup, an attacker gaining direct access to the James server would trivially
access user accounts. Client headers propagation furthermore needs to be done with care.

The use of an API gateway significantly ease the implementation on James side, as we no longer need to take care of 
complex OIDC topics, like key management using JWKS endpoints, token revocation (which would need some kind of shared 
memory storage), JWT validation and claim extraction.

Regarding IMAP and SMTP, the proposed strategy means we need to add optional additional configuration for IMAP and SMTP
 - OIDC configuration endpoint
 - OIDC JWKS endpoint
 - JWT claim to be using as a username.
 
We do not target to work on JWT token revocation for IMAP and SMTP. This makes less sense than for HTTP as IMAP and SMTP 
are connected protocols. This would require extra work that would come and complement this work.

## Alternatives

### Handle OIDC within an Authentication strategy using JWKS and a backchannel for token invalidation

Querying the JWKS endpoint, we would get the public key and use it to validate the JWT tokens.

Also, token invalidation would need to be taken into account. An HTTP endpoint then needs to be used as a callback by 
the OIDC provider to revoke tokens in each application (webadmin endpoints?). Revoked token would need to be stored, 
and we likely would need an additional datastore for this use case (Redis?).

Relying on the API gateway allows us not to worry now about these two concerns.

Nothing forbids people to implement OIDC into James themselves thanks to the modular authentication strategy.

### Handle OIDC within an Authentication strategy and call the OIDC userinfo endpoint

The idea here is to fully delegate the token validation to the OIDC provider. The application server requests the OIDC 
provider userinfo endpoint with the credentials given by the clients.

Upsides: ease of implementation. Just an HTTP call, no crypto to be taking care of, no revokation to screw up.

Downside: performance. Each request ends up calling the OIDC provider that then can be overloaded. This is definitely to 
avoid with HTTP, but could make sense with IMAP/SMTP that are longer lived connections. 

## References

 - OpenId spec: https://openid.net/specs/openid-connect-core-1_0.html
 - Krakend: https://www.krakend.io/
 - Keycloack: https://www.keycloak.org/
 - [RFC-7628] SASL OATH mechanism for SMTP and IMAP: https://datatracker.ietf.org/doc/html/rfc7628
 - Krakend configured with keycloack: https://www.krakend.io/docs/authorization/keycloak/
 - Krakend configured with token revocation: https://www.krakend.io/docs/authorization/revoking-tokens/

Resources linked to this ADR:

 - Mailing list discussions: https://www.mail-archive.com/server-dev@james.apache.org/msg71313.html
 - JIRA: https://issues.apache.org/jira/browse/JAMES-3680
 - PR of the ADR: https://github.com/apache/james-project/pull/778