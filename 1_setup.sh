#!/bin/bash
./gradlew build || exit 1

python_env="python_plot_environment"
if [ -d "${python_env}" ]; then
    source ${python_env}/bin/activate
else
    python3 -m venv ${python_env}
    source ${python_env}/bin/activate
    pip3 install -r requirements.txt
fi
