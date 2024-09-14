#!/bin/bash
config_dir='config'

java_args="-Xmx12g -jar build/libs/evaluation-coverage-metrics-1.0-all.jar"
args="${java_args} --config_dir ${config_dir} --config"

./gradlew build || exit 1

java ${args} clean
java ${args} prepare
java ${args} time
java ${args} sample
java ${args} partial_coverage

cur_results_dir=$(<"results/.current")
rm  ${cur_results_dir}.zip 2> /dev/null
zip -r -q -o ${cur_results_dir}.zip results/${cur_results_dir}
