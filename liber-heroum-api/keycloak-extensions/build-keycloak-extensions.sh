#!/bin/sh
set -eu
cd "$(dirname "$0")"
providers=../docker-compose/keycloak/providers
mkdir -p "$providers"
for module in */; do
  docker run --rm \
    -v "$PWD/$module":/build \
    -v "$HOME/.m2":/root/.m2 \
    -w /build \
    maven:3.9-eclipse-temurin-21 \
    mvn -q -B package
  cp "$module"target/*.jar "$providers"/
done
ls -l "$providers"
