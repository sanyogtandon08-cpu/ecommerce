#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build
javac --add-modules jdk.httpserver -d build src/main/java/shop/ShopServer.java
mkdir -p build/web
cp -R src/main/resources/web/. build/web/
exec java --add-modules jdk.httpserver -cp build shop.ShopServer
