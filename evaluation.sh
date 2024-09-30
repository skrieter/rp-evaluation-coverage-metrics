#!/bin/bash
source ./1_setup.sh || exit 1
./2_run.sh || exit 1
./3_plot.sh || exit 1
