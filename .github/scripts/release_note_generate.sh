#!/bin/bash

# Check if at least one argument is provided
if [ $# -lt 1 ]; then
  echo "Usage: $0 <previous release> [latest commit or omit for master]"
  exit 1
fi

# Assign the first argument to variable1
previous_release=$1

# Check if the second argument is provided, otherwise use default value
if [ -z "$2" ]; then
  latest_commit="master"
else
  latest_commit=$2
fi

curl -H "Accept: application/vnd.github.v3+json" \
            "https://api.github.com/repos/komikku-app/komikku/compare/$previous_release...$latest_commit" \
            | jq '[.commits[]|{message:(.commit.message | split("\n")), username:.author.login}]' \
            | jq -r '.[]|"- \(.message | first) (@\(.username))"'
