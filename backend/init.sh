#!/bin/bash
set -e

echo "Verificando entorno de migracion..."

BUILD_CMD="mvn"
TEST_CMD="mvn test"

echo ""
echo "Build: $BUILD_CMD clean compile"
$BUILD_CMD clean compile

echo ""
echo "Tests: $TEST_CMD"
$TEST_CMD

echo ""
echo "OK. Proyecto listo para trabajar."
