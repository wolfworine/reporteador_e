#!/bin/bash
set -e

echo "Verificando entorno de migracion..."

BUILD_CMD="npm run build"
TEST_CMD="npm test"

echo ""
echo "Build: $BUILD_CMD"
$BUILD_CMD

echo ""
echo "Tests: $TEST_CMD"
$TEST_CMD

echo ""
echo "OK. Proyecto listo para trabajar."
