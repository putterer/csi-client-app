import matplotlib as mpl
import matplotlib.pyplot as plot
import numpy as np
from scipy.constants import speed_of_light
from math import sqrt
from scipy.constants import speed_of_light
from sklearn.mixture import GaussianMixture
from util import eprint

EXPECTED_CLUSTERS = 5

THETA_SCALE_FACTOR = 1.0 / (2*np.pi)
TAU_SCALE_FACTOR = speed_of_light / 40

INDIVIDUAL_FILTER_LIMIT_THETA = 20.0
INDIVIDUAL_FILTER_LIMIT_TAU = 2.0 / speed_of_light

CLUSTER_SPREAD_DISTANCE_LIMIT = 0.2

def filterIndividualPoints(peaks):
    filtered_peaks = []
    removed_peaks = []
    for peak in peaks:
        dist_to_nearest_theta = min([abs(peak[0] - other[0]) if peak is not other else float("inf") for other in peaks])
        dist_to_nearest_tau = min([abs(peak[1] - other[1]) if peak is not other else float("inf") for other in peaks])
        if dist_to_nearest_theta > INDIVIDUAL_FILTER_LIMIT_THETA or dist_to_nearest_tau > INDIVIDUAL_FILTER_LIMIT_TAU:
            removed_peaks.append(peak)
        else:
            filtered_peaks.append(peak)
    return filtered_peaks, removed_peaks

def filterSpread(peaks):
    if len(peaks) == 0:
        return peaks, []

    # Calculate distance to all neighbors
    neighbor_dists = [[manhattanNorm2D(peak, other) for other in peaks] for peak in peaks]
    neighbor_dists_max = [max(peak_dists) for peak_dists in neighbor_dists]

    # Invert distance (closer = better)
    neighbor_dists = [[neighbor_dists_max[peak] - neighbor_dists[peak][other] if neighbor_dists[peak][other] < CLUSTER_SPREAD_DISTANCE_LIMIT else 0.0 for other in range(len(peaks))] for peak in range(len(peaks))]

    for i in range(len(peaks)):
        neighbor_dists[i][i] = 0.0

    # Sum and weight
    total_dists = [sum(peak_dists) for peak_dists in neighbor_dists]
    neighbor_weights = [[neighbor_dists[peak][other] / neighbor_dists_max[peak] if total_dists[peak] != 0.0 else 0.0 for other in range(len(peaks))] for peak in range(len(peaks))]

    peak_values = np.ones((len(peaks)))
    for round in range(3):
        peak_values /= 2.0  # Half of the value goes to neighbors

        new_peak_values = np.array([v for v in peak_values])
        for i in range(len(peaks)):
            for neighbor in range(len(peaks)):
                new_peak_values[neighbor] += peak_values[i] * neighbor_weights[i][neighbor]  # peak_values is already halved

        peak_values = new_peak_values
    eprint(peak_values)

    peak_value_average = sum(peak_values) / len(peak_values)  # TODO: this is cutting half of all values
    filtered_peaks = []
    removed_peaks = []
    for i in range(len(peaks)):
        if peak_values[i] < peak_value_average:
            removed_peaks.append(peaks[i])
        else:
            filtered_peaks.append(peaks[i])

    return filtered_peaks, removed_peaks

def clusterGMM(peaks):
    X_train = [np.array((peak[0] * THETA_SCALE_FACTOR, peak[1] * TAU_SCALE_FACTOR)) for peak in peaks]
    gmm = GaussianMixture(n_components=(min(EXPECTED_CLUSTERS, len(peaks))))
    gmm.fit(X_train)

    # eprint(gmm.means_)
    # eprint("\n")
    # eprint(gmm.covariances_)

    cluster_prediction = gmm.predict_proba(X_train)

    weight_per_mean = []  # Sum of all probabilities, NOT normalized!
    height_per_mean = []
    variances = []
    for i in range(len(gmm.means_)):
        weight = sum(cluster_prediction[:, i])
        weight_per_mean.append(weight)

        height = 0
        for j in range(len(peaks)):
            height += cluster_prediction[j, i] * peaks[j][2]
        height_per_mean.append(height / weight)

    scaled_means = [[mean[0] / THETA_SCALE_FACTOR, mean[1] / TAU_SCALE_FACTOR] for mean in gmm.means_]
    # cov(aX, bY) = ab cov(X, Y)
    scaled_covariances = [[[var[0][0]/THETA_SCALE_FACTOR/THETA_SCALE_FACTOR, var[0][1]/THETA_SCALE_FACTOR/TAU_SCALE_FACTOR], [var[1][0]/TAU_SCALE_FACTOR/THETA_SCALE_FACTOR, var[1][1]/TAU_SCALE_FACTOR/TAU_SCALE_FACTOR]] for var in gmm.covariances_]

    return scaled_means, scaled_covariances, weight_per_mean, height_per_mean

def manhattanNorm2D(p1, p2):
    return abs(p1[0] - p2[0]) * THETA_SCALE_FACTOR + abs(p1[1] - p2[1]) * TAU_SCALE_FACTOR

def euclideanNorm2D(p1, p2):
    return sqrt(pow(p1[0] - p2[0], 2) * THETA_SCALE_FACTOR + pow(p1[1] - p2[1], 2)) * TAU_SCALE_FACTOR