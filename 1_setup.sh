#!/bin/bash
./gradlew build || exit 1

partial_coverage="results/2024-09-25_11-29-30/data/data-2024-09-26_10-55-02/part_aa"
if [ -f "$partial_coverage" ]; then
  ./gradlew combineZipParts
fi

python_env="python_plot_environment"
if [ -d "${python_env}" ]; then
    source ${python_env}/bin/activate
else
    python3 -m venv ${python_env}
    source ${python_env}/bin/activate
    pip3 install -r requirements.txt
fi
