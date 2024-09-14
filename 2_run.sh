#!/bin/bash

# Settings
config_dir='config'

args="--config_dir ${config_dir} --config"

# Run evaluation
./gradlew run --args="${args} clean" || exit 1
./gradlew run --args="${args} prepare" || exit 1
./gradlew run --args="${args} time" || exit 1
./gradlew run --args="${args} sample" || exit 1
./gradlew run --args="${args} partial_coverage" || exit 1
