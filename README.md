# Evaluation of different t-wise coverage metrics

## Structure
+ __build__\
  Build folder produced by gradle. Contains the compiled, executable jar file.
  - __libs/evaluation-coverage-metrics-1.0-all.jar__\
  The executable jar file.
+ __config__\
  Contains configuration files for the evaluation phases.
+ __gradle__\
  Contains files for the gradle wrapper.
+ __models__\
  Contains the feature models of the configurable systems used in the evaluation.
+ __results__\
  Result folder created by running the evaluation. Contains the raw data and generated plots.
  + __"timestamp"/data__\
    Contains csv files with the raw data produced by the evaluation.
  + __"timestamp"/gen__\
    Contains samples created by the evaluation.
  + __"timestamp"/plot__\
    Contains the plots created by the python plot script.
+ __src__\
  Contains the source files.

## Prerequisites
To run this evaluation you require the following packages on your systems:
- Python, Version 3

## Setup
Before running the replication package needs some setup.
For this the following commands need to be run:
```
chmod +x 0_rp-setup.sh
./0_rp-setup.sh
```

## Run the Complete Evaluation
To run the complete evaluation execute the `evaluation.sh` script, which will perform the setup, run the actual evaluation, and produce the plots:
```
./evaluation.sh
```
**or** run the stages of this evaluation separately:

```
source ./1_setup.sh
./2_run.sh
./3_plot.sh
```

## Step by Step

### Build and Setup
Execute the following script to unpack the rp-data:
```
chmod +x 0_rp-setup.sh
./0_rp-setup.sh
```

Execute the following gradle task to build the executable jar file and unzip the `model.zip` containing the feature model:
```
./gradlew build
```
The resulting Jar can be found in `build/libs/evaluation-coverage-metrics-1.0-all.jar` and can be called directly.

To set up the python environment for plotting the data run:
```
python3 -m venv python_plot_environment
source python_plot_environment/bin/activate
pip3 install -r requirements.txt
```
This creates and activates a new virtual python environment named _python_plot_environment_ and installs all necessary python libraries from the `requirements.txt`.

Once set up, you only need to activate the python environment again:
```
source python_plot_environment/bin/activate
```

### Run
The evaluation is run in multiple phases. To run a phase execute the follwing gradle task:
```
./gradlew run --args="--config_dir config --config <phase_name>"
```

The following phases exist and should be run in order for the complete evaluation:

1. clean
2. prepare
3. time
4. sample
5. partial_coverage

A new directory `results` will be created, containing all generated files.

### Create Plots

Once all phases ran successfully, plots can be created using the following command:
```
python3 plot.py
```
The plot can be found in `results/"time-stamp"/plot`

The data used in our paper can be found in `results/2024-09-25_11-29-30`