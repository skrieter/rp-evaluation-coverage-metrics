#!/bin/bash
chmod +x 1_setup.sh
chmod +x 2_run.sh
chmod +x 3_plot.sh
chmod +x evaluation.sh
chmod +x gradlew

cat results/2024-09-25_11-29-30/data/data-2024-09-26_10-55-02/part_* > results/2024-09-25_11-29-30/data/data-2024-09-26_10-55-02/partial_coverage.zip
unzip results/2024-09-25_11-29-30/data/data-2024-09-26_10-55-02/partial_coverage.zip -d results/2024-09-25_11-29-30/data/data-2024-09-26_10-55-02/