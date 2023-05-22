#!/bin/sh

set -eux

docker-compose up -d

GET_TOKEN_RESPONSE=`curl --location 'http://sso.example.com:8080/auth/realms/oidc/protocol/openid-connect/token' \
                                  --header 'Content-Type: application/x-www-form-urlencoded' \
                                  --data-urlencode 'grant_type=password' \
                                  --data-urlencode 'scope=openid profile email' \
                                  --data-urlencode 'client_id=james-thunderbird' \
                                  --data-urlencode 'client_secret=Xw9ht1veTu0Tk5sMMy03PdzY3AiFvssw' \
                                  --data-urlencode 'username=james-user@localhost' \
                                  --data-urlencode 'password=secret' 2>/dev/null`

ACCESS_TOKEN=`echo $GET_TOKEN_RESPONSE 2>/dev/null |perl -pe 's/^.*"access_token"\s*:\s*"(.*?)".*$/$1/'`
REFRESH_TOKEN=`echo $GET_TOKEN_RESPONSE 2>/dev/null |perl -pe 's/^.*"refresh_token"\s*:\s*"(.*?)".*$/$1/'`

echo "Got an access_token"
if curl -H "Authorization: Bearer $ACCESS_TOKEN" http://sso.example.com:8080/auth/realms/oidc/protocol/openid-connect/userinfo 2>/dev/null| grep james-user >/dev/null; then
	echo "Access_token is valid"
else
	echo "ACCESS_TOKEN VERIFICATION FAILED"
fi

echo -n "Trying James: "

APISIX_JMAP_ENDPOINT=apisix.example.com:9080/oidc/jmap/session
if curl -v -H 'Accept: application/json; jmapVersion=rfc-8621' -H "Authorization: Bearer $ACCESS_TOKEN" $APISIX_JMAP_ENDPOINT 2>/dev/null | grep uploadUrl >/dev/null; then
	echo "OK"
else
	echo "Not OK"
fi

# Logout

curl --location 'http://sso.example.com:8080/auth/realms/oidc/protocol/openid-connect/logout' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'client_id=james-thunderbird' \
--data-urlencode 'client_secret=Xw9ht1veTu0Tk5sMMy03PdzY3AiFvssw' \
--data-urlencode 'refresh_token='$REFRESH_TOKEN  >/dev/null 2>&1

sleep 1

if curl -v -H 'Accept: application/json; jmapVersion=rfc-8621' -H "Authorization: Bearer $ACCESS_TOKEN" $APISIX_JMAP_ENDPOINT 2>/dev/null | grep uploadUrl >/dev/null; then
	echo "LOGOUT FAILED"
else
	echo "Logout OK"
fi
