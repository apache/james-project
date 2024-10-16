#!/bin/sh -e

# Deploy release builds to Sonatype (and not the staging repo)
mvn $1 -Prelease -Prelease-sign-artifacts release:clean release:prepare release:perform


# To redeploy without touching git (tags, commits, etc), run:
#   mvn versions:set -DnewVersion=<VERSION>
#   mvn -B -Prelease -Prelease-sign-artifacts deploy -DskipTests=true
#   mvn versions:revert
