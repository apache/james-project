#!/bin/sh -e

printUsage() {
   echo "Usage : "
   echo "./merge.sh SHA1 RESULTING_BRANCH ORIGINAL_BRANCH"
   echo "    SHA1: SHA1 to merge with the branch"
   echo "    RESULTING_BRANCH : Resulting branch of the merge"
   echo "    ORIGINAL_BRANCH: the original branch used for merge (if none, then `master` will be used)"
   exit 1
}

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
    printUsage
fi

SHA1=$1
RESULTING_BRANCH=$2
if [ "$#" -eq 3 ]; then
    ORIGINAL_BRANCH=$3
else
    ORIGINAL_BRANCH=master
fi

APACHE_REPO=`git remote show | grep apache || true`
if [ -z "$APACHE_REPO" ]; then
    git remote add apache https://github.com/apache/james-project.git
fi 
# Getting original branch from apache repo
git fetch apache
git checkout apache/$ORIGINAL_BRANCH -B $ORIGINAL_BRANCH

# Getting the branch to be merged from /origin
git fetch origin
# This is required for non master branches but fails for SHA-1
git checkout origin/$SHA1 -b $SHA1 || true
git checkout $SHA1
git checkout -b SHA1_BRANCH

# Merging the branch to be merged in the original branch
git checkout $ORIGINAL_BRANCH
git checkout -b $RESULTING_BRANCH
git merge --no-edit SHA1_BRANCH
