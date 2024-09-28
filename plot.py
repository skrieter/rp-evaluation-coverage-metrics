import os
import sys
from dataclasses import dataclass

from plotnine import *
import numpy as np
import pandas as pd
import matplotlib
from scipy.stats import ttest_rel


@dataclass
class Config:
    root_dir_name: str
    out_dir_name: str
    save_results: bool
    show_results: bool
    colors: list
    pdf_width: int
    pdf_height: int

    def __init__(self, argv):
        if len(argv) > 1:
            self.root_dir_name = argv[1]
        else:
            self.root_dir_name = 'data'
            if os.path.exists('results/.current'):
                with open('results/.current') as f:
                    data_dir_name = f.readline()
                    self.root_dir_name = 'results/' + data_dir_name

        self.out_dir_name = self.root_dir_name + '/plot/'

        self.show_results = False
        self.save_results = True
        self.pdf_width = 400
        self.pdf_height = 200

def set_graphics_options():
    pd.set_option('display.max_columns', None)
    pd.set_option('display.max_rows', None)
    pd.set_option('display.max_colwidth', None)

    font = {'size'   : 34}
    matplotlib.rc('font', **font)


def readCSVs(file_name, dtype_spec):
    data_files = []
    for dirpath, _, filenames in os.walk(config.root_dir_name + "/data"):
        if file_name in filenames:
            data_files.append(os.path.join(dirpath, file_name))

    print(data_files)
    data_frames = [pd.read_csv(file, dtype=dtype_spec, sep = ',') for file in data_files]
    combined_data_frame = pd.concat(data_frames, ignore_index=True)
    combined_data_frame = combined_data_frame.drop_duplicates()
    return combined_data_frame


def prepare_data():
    dtype_samples = {
        'SystemID': 'int16',
        'T': 'int8',
        'SystemIteration': 'int8',
        'Error': 'bool',
        'Timeout': 'bool',
        'Size': 'int32',
    }

    dtype_systems = {
        'SystemID': 'int16',
        'SystemName': 'str',
        'VariableCount': 'int32',
        'ClauseCount': 'int32',
    }

    dtype_time = {
        'SystemID': 'int16',
        'Iteration': 'str',
        'core': 'int64',
        'atomic': 'int64',
    }

    dtype_metrics = {
        'MetricID': 'int16',
        'Core': 'bool',
        'Dead': 'bool',
        'Abstract': 'category',
        'Atomic': 'category',
        'PC': 'bool',
        'Equal': 'bool',
    }

    dtype_system_to_metric = {
        'SystemID': 'int16',
        'T': 'int8',
        'SystemIteration': 'int8',
        'ShuffleIteration': 'int8',
        'MetricID': 'int16',
        'FilteredVariableCount': 'int32',
        'CoverageID': 'int32',
        'CoverageTime': 'int64',
    }

    dtype_coverage = {
        'CoverageID': 'int32',
        'PartialSampleSize': 'int32',
        'CoveredInteractions': 'int64',
    }

    if not os.path.exists(config.out_dir_name + 'complete.pkl'):
        print('Reading and joining original tables')
        system_to_metric = readCSVs("system_to_metric.csv", dtype_system_to_metric)
        data = system_to_metric
        data['CoverageTime'] = (data['CoverageTime'] / 1_000_000_000)

        samples = readCSVs("samples.csv", dtype_samples).set_index(['SystemID', 'T', 'SystemIteration'])
        data = data.join(samples, on=['SystemID', 'T', 'SystemIteration'], rsuffix="_")
        data = data[(data['Error'] == False) &
                    (data['Timeout'] == False)]
        data = data[['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'MetricID', 'FilteredVariableCount', 'CoverageID', 'Size', 'CoverageTime']]

        partial_coverage = readCSVs("partial_coverage.csv", dtype_coverage).set_index('CoverageID')
        data = data.join(partial_coverage, on='CoverageID', rsuffix="_")

        print('Joining valid interactions for specific metric')
        key = ['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'MetricID']
        df = data[['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'MetricID', 'Size', 'PartialSampleSize', 'CoveredInteractions']]
        df = df[df['Size'] == df['PartialSampleSize']]
        df = df[['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'MetricID', 'CoveredInteractions']]
        data = data.join(df.set_index(key), on=key, rsuffix="_complete_metric")

        print('Joining covered interactions for default metric')
        key = ['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'PartialSampleSize']
        df = data[['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'PartialSampleSize', 'MetricID', 'CoveredInteractions']]
        df = df[df['MetricID'] == 1]
        df = df[['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'PartialSampleSize', 'CoveredInteractions']]
        data = data.join(df.set_index(key), on=key, rsuffix="_default")

        print('Joining valid interactions for default metric')
        key = ['SystemID', 'T', 'SystemIteration', 'ShuffleIteration']
        df = data[['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'MetricID', 'Size', 'PartialSampleSize', 'CoveredInteractions']]
        df = df[(df['MetricID'] == 1) & (df['Size'] == df['PartialSampleSize'])]
        df = df[['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'CoveredInteractions']]
        data = data.join(df.set_index(key), on=key, rsuffix="_complete_default")

        print('Computing coverage')
        #data['Coverage'] = data['CoveredInteractions'] / data['CoveredInteractions_complete_metric']
        data['Coverage'] = data.apply(calc_coverage, axis=1)
        data['CoverageDiff'] = data['Coverage'] - (data['CoveredInteractions_default'] / data['CoveredInteractions_complete_default'])
        data['InteractionReduction'] = data['CoveredInteractions'] / data['CoveredInteractions_default']
        data['RelaltivePartialSize'] = data['PartialSampleSize'] / data['Size']

        data = data[['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'MetricID', 'FilteredVariableCount', 'Size', 'PartialSampleSize', 'CoveredInteractions', 'Coverage', 'CoverageDiff', 'InteractionReduction', 'RelaltivePartialSize', 'CoverageTime']]

        data = data.dropna()

        metrics = readCSVs("metric.csv", dtype_metrics)
        metrics['Metric'] = metrics.apply(get_metric, axis=1)
        # metric_order = metrics.groupby('Metric', observed=True)['MetricID'].apply(top).sort_values(ascending=True).index.tolist()
        metric_order = ["default","CF","DF","AF","AFS","ALS","PCI",
                        "CF-DF","CF-AF","CF-AFS","CF-ALS","CF-PCI",
                        "DF-AF","DF-AFS","DF-ALS","DF-PCI",
                        "AF-AFS","AF-ALS","AF-PCI",
                        "AFS-PCI","ALS-PCI",
                        "CF-DF-AF","CF-DF-AFS","CF-DF-ALS","CF-DF-PCI","CF-AF-AFS","CF-AF-ALS","CF-AF-PCI","CF-AFS-PCI","CF-ALS-PCI",
                        "DF-AF-AFS","DF-AF-ALS","DF-AF-PCI","DF-AFS-PCI","DF-ALS-PCI",
                        "AF-AFS-PCI","AF-ALS-PCI",
                        "CF-DF-AF-AFS","CF-DF-AF-ALS","CF-DF-AF-PCI","CF-DF-AFS-PCI","CF-DF-ALS-PCI",
                        "DF-AF-AFS-PCI","DF-AF-ALS-PCI",
                        "CF-DF-AF-AFS-PCI","CF-DF-AF-ALS-PCI"
                        ]

        metrics['Metric'] = pd.Categorical(metrics['Metric'], categories=metric_order, ordered=True)
        metrics = metrics.set_index('MetricID')
        data = data.join(metrics, on='MetricID', rsuffix="_")

        analysis_time = readCSVs("analysis_time.csv", dtype_time)
        analysis_time = analysis_time.groupby('SystemID', observed=True).agg({
            'core': 'median',
            'atomic': 'median'})
        analysis_time = analysis_time.rename(columns={"core": "CoreTime", "atomic": "AtomicTime"})
        analysis_time['CoreTime'] = (analysis_time['CoreTime'] / 1000000000)
        analysis_time['AtomicTime'] = (analysis_time['AtomicTime'] / 1000000000)
        data = data.join(analysis_time, on='SystemID', rsuffix="_")
        data['MetricTime'] = data.apply(add_times, axis=1)

        systems = readCSVs("systems.csv", dtype_systems).set_index('SystemID')
        system_order = systems.groupby('SystemName', observed=True)['VariableCount'].apply(top).sort_values(ascending=True).index.tolist()
        systems['SystemName'] = pd.Categorical(systems['SystemName'], categories=system_order, ordered=True)
        data = data.join(systems, on='SystemID', rsuffix="_")

        data = data[['SystemID', 'SystemName', 'VariableCount', 'ClauseCount', 'T', 'SystemIteration', 'ShuffleIteration', 'MetricID', 'Metric', 'CoverageTime', 'MetricTime', 'FilteredVariableCount', 'Size', 'PartialSampleSize', 'CoveredInteractions', 'Coverage', 'CoverageDiff', 'InteractionReduction', 'RelaltivePartialSize']]

        print('Writing complete table')
        create_out_dir()
        data.to_pickle(config.out_dir_name + 'complete.pkl', compression='gzip')
        systems.to_pickle(config.out_dir_name + 'systems.pkl', compression='gzip')
        metrics.to_pickle(config.out_dir_name + 'metrics.pkl', compression='gzip')

    print('Reading complete table')
    data = pd.read_pickle(config.out_dir_name + 'complete.pkl', compression='gzip')
    systems = pd.read_pickle(config.out_dir_name + 'systems.pkl', compression='gzip')
    metrics = pd.read_pickle(config.out_dir_name + 'metrics.pkl', compression='gzip')

    print("========================================")
    metrics.info(verbose=True, memory_usage="deep")
    print("----------------------------------------")
    systems.info(verbose=True, memory_usage="deep")
    print("----------------------------------------")
    data.info(verbose=True, memory_usage="deep")
    print("========================================")

    return [data, systems, metrics]


def get_metric(row):
    metric =(('CF ' if row['Core'] == True else '') + ('DF ' if row['Dead'] == True else '') + ('AF ' if row['Abstract'] == 'abstrakt' else '') + ('ConF ' if row['Abstract'] == 'concrete' else '') + ('AFS ' if row['Atomic'] == 'features' else '') + ('ALS ' if row['Atomic'] == 'literals' else '') + ('PCI ' if row['PC'] == True else '') + ('EFI ' if row['Equal'] == True else '')).strip().replace(' ', '-')
    return 'default' if not metric else metric


def calc_coverage(row):
    return (row['CoveredInteractions'] / row['CoveredInteractions_complete_metric']) if row['CoveredInteractions_complete_metric'] != 0 else 0


def add_times(row):
    time = row['CoverageTime'] + ((row['CoreTime'] if row['Core'] == True or row['Dead'] == True else 0) + (row['AtomicTime'] if row['Atomic'] != 'none' else 0))
    return time


def top(series):
    return series.iloc[0]


def create_out_dir():
    if not os.path.exists(config.out_dir_name):
        try:
            os.mkdir(config.out_dir_name)
        except OSError:
            print ("Failed to create output directory %s" % output_path)
            os.exit(-1)


def create_plot(name, p, ratio, width=400, height=200):
    create_out_dir()

    if config.show_results:
        p.show()

    if config.save_results:
        file_name = config.out_dir_name + name + '.pdf'
        print('Writing ' + file_name)
        p.save(file_name, verbose = False, width = width, height = height, units = 'mm', dpi = 300)


def create_csv(df, name):
    create_out_dir()

    if config.show_results:
        print(df)

    if config.save_results:
        df.to_csv(config.out_dir_name + name + '.csv', index=False, sep = ';')


def create_table(df, name):
    create_out_dir()

    table = df.style.format(decimal='.', thousands=',', precision=2, escape="latex").to_latex(multicol_align='c')

    if config.show_results:
        print(table)

    if config.save_results:
        with open(config.out_dir_name + name + '.tex', 'w') as f:
            print(table, file=f)


def plot_system_statistics():
    create_plot('system_statistics', (
        ggplot(systems, aes('VariableCount', 'ClauseCount'))
        + geom_point()
        + xlab("Number of Features")
        + ylab("Number of Clauses in CNF")
        + theme(
            axis_title_x=element_text(size=14),
            axis_title_y=element_text(size=14),
            strip_text_x=element_text(size=12),
            text=element_text(size=12)
        )
#        + ggtitle("Feature Model Statistics")
    ), 1)


def plot_system_core():
    create_plot('system_statistics', (
        ggplot(systems, aes('VariableCount', 'ClauseCount'))
        + geom_point()
        + xlab("Number of Features")
        + ylab("Number of Clauses in CNF")
        + theme(
            axis_title_x=element_text(size=14),
            axis_title_y=element_text(size=14),
            strip_text_x=element_text(size=12),
            text=element_text(size=12)
        )
#        + ggtitle("Feature Model Statistics")
    ), 1)


def plot_coverage_per_system():
    df_plot = data.groupby(['SystemName', 'T', 'SystemIteration', 'ShuffleIteration', 'MetricID'], observed=True)['Coverage'].median().reset_index()

    create_plot('coverage_per_system', (
        ggplot(df_plot, aes('SystemName', 'Coverage'))
        + geom_point()
        + theme(axis_text_x=element_text(rotation=30, hjust=1))
        + facet_grid(cols='T', labeller=labeller(cols=(lambda v : 't = ' + v)))
        + xlab("Feature Model")
        + ylab("Interaction Reduction")
        + theme(
            axis_title_x=element_text(size=14),
            axis_title_y=element_text(size=14),
            strip_text_x=element_text(size=12),
            text=element_text(size=12)
        )
#        + ggtitle("Coverage per Feature Model")
    ), 1)


def plot_coverage_per_metric():
    df_plot = data.groupby(['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'Metric'], observed=True)['Coverage'].median().reset_index()

    df_plot = df_plot[df_plot['Metric'].isin(['default','CF-DF','AF','ALS','CF-DF-ALS','PCI','CF-DF-AF-ALS','CF-DF-AF-ALS-PCI'])]

    create_plot('coverage_per_metric', (
        ggplot(df_plot, aes('Metric', 'Coverage'))
        + geom_boxplot()
        + theme(axis_text_x=element_text(rotation=30, hjust=1))
        + facet_grid(cols='T', labeller=labeller(cols=(lambda v : 't = ' + v)))
        + xlab("Metric")
        + ylab("Coverage")
        + theme(
            axis_title_x=element_text(size=18),
            axis_title_y=element_text(size=18),
            strip_text_x=element_text(size=14),
            text=element_text(size=14),
        )
        + scale_y_continuous(breaks=[0.5,0.6,0.8,1.0],labels = ['50%','60%','80%','100%'], limits=(0.5, 1.0))
#        + ggtitle("Coverage per Metric")
    ), 1)


def plot_relative_coverage_per_metric():
    df_plot = data.groupby(['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'Metric'], observed=True)['CoverageDiff'].median().reset_index()
    df_plot = df_plot[df_plot['Metric'].isin(['CF-DF', 'AF', 'ALS', 'CF-DF-ALS', 'PCI', 'CF-DF-AF-ALS', 'CF-DF-AF-ALS-PCI'])]

    annotation_df = pd.DataFrame({
        'T': [3]
    })

    create_plot('relative_coverage_per_metric', (
            ggplot(df_plot, aes('Metric', 'CoverageDiff'))
            + geom_boxplot()
            + theme(axis_text_x=element_text(rotation=30, hjust=1))
            + facet_grid(cols='T', labeller=labeller(cols=(lambda v: 't = ' + v)))
            + xlab("Metric")
            + ylab("Coverage Difference")
            + scale_y_continuous(labels=lambda l: ["%d%%" % (v * 100) for v in l])
            + theme(
            axis_title_x=element_text(size=18),
            axis_title_y=element_text(size=18),
            strip_text_x=element_text(size=14),
            text=element_text(size=14),
        )
            + geom_label(
                data=annotation_df,
                x=5.3,
                y=0.1325,
                label='only 36 out of 48 models scaled for t=3',
                fill='white',
                color='black',
                size=11,
            )
            # + geom_text(label="only 36 out of 48 models scaled for t=3", x=4.8, y=0.12, data=annotation_df, color='black')
    ), 1)




def plot_interaction_reduction_per_metric():
    df_plot = data[data['Size'] == data['PartialSampleSize']]
    df_plot = df_plot.groupby(['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'Metric'], observed=True)['InteractionReduction'].median().reset_index()
    df_plot = df_plot[df_plot['Metric'].isin(['CF-DF','AF','ALS','CF-DF-ALS','PCI','CF-DF-AF-ALS','CF-DF-AF-ALS-PCI'])]

    create_plot('interaction_reduction_per_metric', (
        ggplot(df_plot, aes('Metric', 'InteractionReduction'))
        + geom_boxplot()
        + theme(axis_text_x=element_text(rotation=30, hjust=1))
        + facet_grid(cols='T', labeller=labeller(cols=(lambda v : 't = ' + v)))
        + xlab("Metric")
        + ylab("Interaction Ratio")
        + theme(
            axis_title_x=element_text(size=14),
            axis_title_y=element_text(size=14),
            strip_text_x=element_text(size=12),
            text=element_text(size=12)
        )
#       + ggtitle("Number of Interactions Relative to Default Metric")
    ), 1)

def plot_interaction_reduction_per_metric_t2():
    df_plot = data[(data['Size'] == data['PartialSampleSize']) & (data['T'] == 2)]
    df_plot = df_plot.groupby(['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'Metric'], observed=True)['InteractionReduction'].median().reset_index()
    df_plot = df_plot[df_plot['Metric'].isin(['CF-DF','AF','ALS','CF-DF-ALS','PCI','CF-DF-AF-ALS','CF-DF-AF-ALS-PCI'])]

    create_plot('interaction_reduction_per_metric_t2', (
            ggplot(df_plot, aes('Metric', 'InteractionReduction'))
            + geom_boxplot()
            + theme(axis_text_x=element_text(rotation=30, hjust=1))
            + xlab("Metric")
            + ylab("Percentage of Interactions")
            + scale_y_continuous(breaks=[0.0,0.25,0.5,0.75,1.0], labels = ['0%','25%','50%','75%','100%'], limits=[0.0,1.0])
            + theme(
                axis_title_x=element_text(size=18),
                axis_title_y=element_text(size=18),
                strip_text_x=element_text(size=14),
                text=element_text(size=14),
            )
        #       + ggtitle("Number of Interactions Relative to Default Metric")
    ), 1)


def plot_interaction_reduction_per_system():
    df_plot = data[data['Size'] == data['PartialSampleSize']]
    df_plot = df_plot.groupby(['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'Metric'], observed=True).agg({
        'VariableCount': top,
        'InteractionReduction': 'median'}).reset_index()

    df_plot = df_plot[df_plot['Metric'].isin(['CF-DF-AF-ALS-PCI','CF-DF'])]
    df_plot = df_plot.dropna()
    df_plot['Metric'] = df_plot['Metric'].cat.remove_unused_categories()

    create_plot('interaction_reduction_per_system', (
            ggplot(df_plot, aes('VariableCount', 'InteractionReduction', color='Metric', shape='Metric'))
            + geom_point()
            + theme(axis_text_x=element_text(rotation=30, hjust=1))
            + facet_grid(cols='T', labeller=labeller(cols=(lambda v : 't = ' + v)))
            + scale_colour_manual(values = ('blue', 'green', 'red'))
            + scale_shape_manual(values = ('o', '+', '^'))
            + xlab("Number of Features")
            + ylab("Interaction Ratio")
            + theme(
                axis_title_x=element_text(size=18),
                axis_title_y=element_text(size=18),
                strip_text_x=element_text(size=14),
                text=element_text(size=14),
            )
#            + ggtitle("Number of Interactions Relative to Default Metric")
    ), 1)

def custom_format(x, pos):
    return f'10^{int(x)}'

def plot_interactions_per_system():
    df_plot = data[data['Size'] == data['PartialSampleSize']]
    df_plot = df_plot.groupby(['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'Metric'], observed=True).agg({
        'VariableCount': top,
        'CoveredInteractions': 'median'}).reset_index()

    df_plot = df_plot[df_plot['Metric'].isin(['CF-DF-AF-ALS-PCI','default'])]
    df_plot = df_plot.dropna()
    df_plot['Metric'] = df_plot['Metric'].cat.remove_unused_categories()
    df_plot['T'] = pd.Categorical(df_plot['T'])

    create_plot('interactions_per_system', (
            ggplot(df_plot, aes('VariableCount', 'CoveredInteractions', color='T', shape='Metric'))
            + geom_point(size=4)  # Increase point size
            + theme(axis_text_x=element_text(rotation=30, hjust=1),
                    legend_position=(0.1, 0.9),
                    legend_box_margin=5)
            + theme(
                axis_title_x=element_text(size=18),
                axis_title_y=element_text(size=18),
                strip_text_x=element_text(size=14),
                text=element_text(size=14),
                legend_background=element_rect(color='black')
            )
            + labs(color='t')
            + scale_colour_manual(values=('#0072B2', '#D95F02', '#009E73'))  # Color-blind friendly palette
            + scale_shape_manual(values=('o', '+', '^'))  # Triangles, diamonds, circles
            + scale_x_log10(labels=lambda x: [f'10^{int(np.log10(y))}' for y in x])
            + scale_y_log10(labels=lambda x: [f'10^{int(np.log10(y))}' for y in x], breaks=[10**i for i in [1, 2, 3, 6, 9]])
            + xlab("Number of Features")
            + ylab("Considered Interactions")
    ), 1, 200, 200)




def plot_variable_reduction_per_metric():
    df_plot = data[data['Size'] == data['PartialSampleSize']]
    df_plot = df_plot.groupby(['SystemID', 'Metric'], observed=True).agg({
    'VariableCount': top,
    'FilteredVariableCount': top}).reset_index()

    df_plot = df_plot[df_plot['Metric'].isin(['CF-DF','AF','ALS','CF-DF-ALS','PCI','CF-DF-AF-ALS'])]

    df_plot['VariableReduction'] = df_plot['FilteredVariableCount'] / df_plot['VariableCount']

    create_plot('feature_reduction_per_metric', (
        ggplot(df_plot, aes('Metric', 'VariableReduction'))
        + geom_boxplot()
        + theme(axis_text_x=element_text(rotation=30, hjust=1))
        + xlab("Metric")
        + ylab("Feature Ratio")
        + theme(
            axis_title_x=element_text(size=14),
            axis_title_y=element_text(size=14),
            strip_text_x=element_text(size=12),
            text=element_text(size=12)
        )
#       + ggtitle("Number of Considered Features Relative to Default Metric")
    ), 1)


def plot_metric_time_per_metric():
    df_plot = data[data['Size'] == data['PartialSampleSize']]
    df_plot = df_plot.groupby(['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'Metric'], observed=True)['MetricTime'].median().reset_index()

    df_plot = df_plot[df_plot['Metric'].isin(['default','CF-DF','AF','ALS','CF-DF-ALS','PCI','CF-DF-AF-ALS','CF-DF-AF-ALS-PCI'])]

    create_plot('metric_time_per_metric', (
        ggplot(df_plot, aes('Metric', 'MetricTime'))
        + geom_boxplot()
        + theme(axis_text_x=element_text(rotation=30, hjust=1))
        + facet_grid(cols='T', labeller=labeller(cols=(lambda v : 't = ' + v)))
        + scale_y_log10()
        + xlab("Metric")
        + ylab("Computation Time (s)")
        + theme(
            axis_title_x=element_text(size=14),
            axis_title_y=element_text(size=14),
            strip_text_x=element_text(size=12),
            text=element_text(size=12)
        )
#        + ggtitle("Metric Computation Time of each Metric")
    ), 1)

def plot_metric_time_per_metric_t2():
    df_plot = data[(data['Size'] == data['PartialSampleSize']) & (data["T"] == 2)]
    df_plot = df_plot.groupby(['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'Metric'], observed=True)['MetricTime'].median().reset_index()

    df_plot = df_plot[df_plot['Metric'].isin(['default','CF-DF','AF','ALS','CF-DF-ALS','PCI','CF-DF-AF-ALS','CF-DF-AF-ALS-PCI'])]

    create_plot('metric_time_per_metric_t2', (
            ggplot(df_plot, aes('Metric', 'MetricTime'))
            + geom_boxplot()
            + theme(axis_text_x=element_text(rotation=30, hjust=1))
            + scale_y_log10()
            + xlab("Metric")
            + ylab("Computation Time (s)")
            + theme(
                axis_title_x=element_text(size=18),
                axis_title_y=element_text(size=18),
                strip_text_x=element_text(size=14),
                text=element_text(size=14),
            )
        #        + ggtitle("Metric Computation Time of each Metric")
    ), 1)


def plot_metric_time_per_system():
    df_plot = data[data['Size'] == data['PartialSampleSize']]
    df_plot = df_plot[df_plot['Metric'].isin(['CF-DF-AF-ALS-PCI','default'])]
    df_plot['Metric'] = df_plot['Metric'].cat.remove_unused_categories()
    df_plot['T'] = pd.Categorical(df_plot['T'])

    df_plot = df_plot.dropna()

    df_plot = df_plot.groupby(['SystemID', 'T', 'Metric'], observed=True).agg({
        'VariableCount': top,
        'MetricTime': 'median'}).reset_index()

    create_plot('metric_time_per_system', (
            ggplot(df_plot, aes('VariableCount', 'MetricTime', color='T', shape='Metric'))
            + geom_point(size=4)
            + theme(axis_text_x=element_text(rotation=30, hjust=1),
                    legend_position=(0.1, 0.9),
                    legend_box_margin=5)
            + theme(
        axis_title_x=element_text(size=22),
        axis_title_y=element_text(size=22),
        strip_text_x=element_text(size=18),
        text=element_text(size=18),
        legend_background=element_rect(color='black')
    )
            + labs(color='t')
            + scale_colour_manual(values=('#0072B2', '#D95F02', '#009E73'))
            + scale_shape_manual(values=('o', '+', '^'))
            + scale_x_log10(labels=lambda x: [f'10^{int(np.log10(y))}' for y in x])
            + scale_y_log10(breaks=[0.00001, 0.1, 1, 10, 100],
                            #labels=["0.00001", "0.1", "1", "10", "100"],
                            limits=[0.00001, 200.0],
                            labels=lambda x: [f'10^{int(np.log10(y))}' for y in x])
            + xlab("Number of Features")
            + ylab("Computation Time (s)")
    ), 1, 200, 200)



def plot_coverage_time_per_metric():
    df_plot = data[data['Size'] == data['PartialSampleSize']]
    df_plot = df_plot.groupby(['SystemID', 'T', 'SystemIteration', 'ShuffleIteration', 'Metric'], observed=True)['CoverageTime'].median().reset_index()

    df_plot = df_plot[df_plot['Metric'].isin(['default','CF-DF','AF','ALS','CF-DF-ALS','PCI','CF-DF-AF-ALS','CF-DF-AF-ALS-PCI'])]

    create_plot('coverage_time_per_metric', (
        ggplot(df_plot, aes('Metric', 'CoverageTime'))
        + geom_boxplot()
        + theme(axis_text_x=element_text(rotation=30, hjust=1))
        + facet_grid(cols='T', labeller=labeller(cols=(lambda v : 't = ' + v)))
        + scale_y_log10()
        + xlab("Metric")
        + ylab("Computation Time (s)")
        + theme(
            axis_title_x=element_text(size=14),
            axis_title_y=element_text(size=14),
            strip_text_x=element_text(size=12),
            text=element_text(size=12)
        )
#        + ggtitle("Coverage Computation Time of each Metric")
    ), 1)


def plot_coverage_time_per_system():
#    df_plot = data[data['Size'] == data['PartialSampleSize']]
    #df_plot = df_plot.groupby(['VariableCount','SystemName', 'T'], observed=True)['CoverageTime'].median().reset_index()
#    df_plot['VariableCount'] = pd.Categorical(df_plot['VariableCount'], ordered=True)

#    create_plot('coverage_time_per_system', (
#        ggplot(df_plot, aes('factor(VariableCount)', 'CoverageTime'))
#        + geom_boxplot()
#        + theme(axis_text_x=element_text(rotation=30, hjust=1))
#        + facet_grid(cols='T', labeller=labeller(cols=(lambda v : 't = ' + v)))
#        + scale_y_log10()
#        + scale_x_continuous(breaks=[100,500,1000,3000])
#        + xlab("Variable Count")
#        + ylab("Computation Time (s)")
#        + ggtitle("Coverage Computation Time for each System")
#    ), 1)

    df_plot = data[data['Size'] == data['PartialSampleSize']]

    df_median = df_plot.groupby(['VariableCount', 'T'], observed=True)['CoverageTime'].median().reset_index()

    create_plot('coverage_time_per_number_of_features', (
            ggplot(df_median, aes('VariableCount', 'CoverageTime'))  # X-axis is VariableCount
            + geom_point(size=3, color='blue')  # Plot a point for the median value
            + theme(axis_text_x=element_text(rotation=30, hjust=1))  # Rotate x-axis labels for readability
            + facet_grid(cols='T', labeller=labeller(cols=(lambda v: 't = ' + str(v))))  # Facet by 'T'
            + scale_y_log10()  # Use log scale for y-axis
            + xlab("Number of Features")  # Label for x-axis
            + ylab("Computation Time (s)")  # Label for y-axis
            + theme(
                axis_title_x=element_text(size=18),
                axis_title_y=element_text(size=18),
                strip_text_x=element_text(size=14),
                text=element_text(size=14),
            )
        # + ggtitle("Median Coverage Computation Time by Variable Count")
    ), 1)



def plot_coverage_per_partial_sample_size_t2():
    df_plot = data[(data['T'] == 2) & ((data['SystemName'] == "axTLS") | (data['SystemName'] == "am31_sim"))]

    df_plot = df_plot.groupby(['SystemName', 'Metric', 'PartialSampleSize'], observed=True).agg({
        'Coverage': 'median',
        'RelaltivePartialSize': 'median'
    }).reset_index()

    df_plot = df_plot[df_plot['Metric'].isin(['default', 'CF-DF-AF-ALS-PCI'])]
    df_plot = df_plot.dropna()
    df_plot['Metric'] = df_plot['Metric'].cat.remove_unused_categories()

    df_test = df_plot.pivot(index=['SystemName', 'PartialSampleSize'], columns='Metric', values='Coverage').reset_index()
    df_test = df_test[['SystemName', 'default', 'CF-DF-AF-ALS-PCI']]

    df_plot['p'] = 1.0
    system_names = df_test['SystemName'].unique()
    for system_name in system_names:
        df_test_filter = df_test[df_test['SystemName'] == system_name]
        stat, p = ttest_rel(df_test_filter['default'], df_test_filter['CF-DF-AF-ALS-PCI'])
        df_plot.loc[df_plot['SystemName'] == system_name, 'p'] = p
    df_plot = df_plot[df_plot['p'] < 0.05]

    # Custom facet labels
    custom_labels = {
        'axTLS': 'axTLS (number of features: 96)',
        'am31_sim': 'am31_sim (number of features: 1178)'
    }

    create_plot('coverage_per_partial_sample_size_t2', (
            ggplot(df_plot, aes('RelaltivePartialSize', 'Coverage', color='Metric'))
            + geom_line()
            + theme(axis_text_x=element_text(rotation=30, hjust=1))
            + facet_wrap('SystemName', ncol=1, labeller=labeller(SystemName=lambda s: custom_labels[s]))  # Custom labeler
            + scale_colour_manual(values=('blue', 'orange', 'green'))
            + xlab("Relative Partial Sample Size")
            + ylab("Pair-wise Coverage")
            + scale_y_continuous(breaks=[0.2,0.4,0.6,0.8,1.0], labels = ['20%','40%','60%','80%','100%'], limits=[0.2,1.0])
            + scale_x_continuous(breaks=[0.0,0.2,0.4,0.6,0.8,1.0], labels = ['0%','20%','40%','60%','80%','100%'])
            + theme(
                axis_title_x=element_text(size=18),
                axis_title_y=element_text(size=18),
                strip_text_x=element_text(size=14),
                text=element_text(size=14),
                legend_position=(0.975, 0.025),
                legend_box_margin=5,
                legend_margin=5,
                legend_background=element_rect(fill='white', size=0.5, color='black')
            )
    ), 1, 200, 200)






if __name__ == "__main__":
    config = Config(sys.argv)
    set_graphics_options()

    dfs = prepare_data()

    data = dfs[0]
    systems = dfs[1]
    metrics = dfs[2]

#    print('Errors in refined table: ' + str([
#        len(data[data['Size']<0]),
#        len(data[data['Coverage']>1]),
#        len(data[data['Coverage']<0]),
#        len(data[data['InteractionReduction']>1])
#    ]))

    print('Ploting')
 #   plot_system_statistics()
 #   plot_coverage_per_system()
 #   plot_coverage_per_metric()
 #   plot_relative_coverage_per_metric()
    plot_interactions_per_system()
 #   plot_interaction_reduction_per_metric()
 #   plot_interaction_reduction_per_metric_t2()
 #   plot_interaction_reduction_per_system()
 #   plot_variable_reduction_per_metric()
 #   plot_coverage_per_partial_sample_size_t2()
 #   plot_metric_time_per_metric()
 #   plot_metric_time_per_metric_t2()
    plot_metric_time_per_system()
 #   plot_coverage_time_per_metric()
 #   plot_coverage_time_per_system()
    print('Finished')
