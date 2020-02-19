import cmath
import json
import multiprocessing as mp
import os
import platform
import sys
import time
from functools import reduce

import numpy as np
from matplotlib import pyplot as plt
from mpl_toolkits import mplot3d
from numpy import linalg
from scipy.constants import speed_of_light
from scipy.ndimage import maximum_filter, morphology
from matplotlib import cm
from util import get_scaled_csi
from util import eprint
from clustering import clusterGMM, filterIndividualPoints, filterSpread
from storage import store, lookup, save_peaks

SUBCARRIERS_USED = 30
SUBCARRIER_FREQ_SPACING = 312.5 * pow(10, 3)   # 312.5 kHz   TODO: why is this different in https://github.com/yuehanlyu/Wifi-Localization/blob/zhaoxin/spotfi_main.py#L19
CHANNEL_FREQUENCY = 2.437 * pow(10, 9)   # 2.437 GHz
ANTENNA_SPACING = 0.084 # 16.8cm over both antennas
# CHANNEL_FREQUENCY = 5.180 * pow(10, 9)   # 5.180 GHz
# ANTENNA_SPACING = 0.095


CLUSTER_WEIGHT_POINTS = 1.0 * 200.0
CLUSTER_WEIGHT_THETA_VAR = 1.0 / (2*np.pi) * 30000.0
CLUSTER_WEIGHT_TAU_VAR = speed_of_light / 20.0 * 1.0  # TODO: needs to be stronger
CLUSTER_WEIGHT_SMALL_TOF = speed_of_light / 20.0 * 20.0

def spotfi(csi, calibration_possibility, lookupfiles, storagefiles):


    eprint("Running spotfi algorithm")
    startTime = time.time()

    scaled_csi = []
    for csi_info in csi:
        csi_matrix = [[[complex(sub['real'], sub['imag']) for sub in rx] for rx in tx] for tx in csi_info['csi_matrix']]
        csi_matrix = np.array(csi_matrix)
        #scaled_csi.append(get_scaled_csi(csi_info['csi_status'], csi_matrix)[:, 0, 0:SUBCARRIERS_USED])
        # TODO: compare
        scaled_csi.append(csi_matrix[:, 0, 0:SUBCARRIERS_USED]) # cut to 1st TX antenna, SUBCARRIERS_USED carriers

    # Sanitize ToF (remove sampling time offset (STO))
    sanitized_csi = list(map(sanitizeToF, scaled_csi))

    # Construct virtual antenna array matrix based on actual csi to be used for music
    smoothed_csi = list(map(smoothCSI, sanitized_csi))

    # computed paths: D, the number of multipaths, E_n is the matrix of eigenvectors corresponding to the M-D smallest eigenvalues
    E_n = []
    computed_paths = []
    for s in smoothed_csi:
        # Construct noise space matrix of covariance matrix X*X_H
        p, E = constructNoiseSubspaceMatrix(s)
        computed_paths.append(p)
        E_n.append(E)

    E_nH = list(map(lambda e: e.conj().T, E_n))  # precompute conjugate transpose

    # The spectrum repeats every PI across theta on average, there are small diversions that,
    # exact repetition appears only after 2 PI
    theta = np.linspace(-1 * np.pi, 1 * np.pi, 92) # -90° to 90° # TODO: -0.5 pi -> 0.5 pi
    tau = np.linspace(-20 / speed_of_light, 20 / speed_of_light, 100) # TODO: choose better

    assert len(theta) % 4 == 0, "theta samples need to be divisible by 4"

    # Load lookup data if present
    lookup_spectrum_db = []
    if len(lookupfiles) == len(E_n):
        eprint("Loading music spectrums from lookup file...")
        for l_file in lookupfiles:
            lookup_spectrum_db.append(lookup(l_file))

    # Calculate music spectrum
    music_spec_db = []
    peaks = []
    batch_count = 4
    batch_size = len(theta) // batch_count
    for packet in range(len(E_n)):

        pool = mp.Pool(batch_count)

        if packet < len(lookup_spectrum_db) and lookup_spectrum_db[packet] is not None:
            eprint("Found lookup music spectrum for packet: ", packet)
            music_spec_db.append(lookup_spectrum_db[packet])
        else:
            eprint("Calculating music spectrum for packet: ", packet)
            music_spec = np.zeros((len(theta), len(tau)))
            music_spec = np.concatenate(tuple([pool.apply(musicSpectrumFuncRange, args=(theta[(i*batch_size):((i+1)*batch_size)], tau, E_n[packet], E_nH[packet])) for i in range(batch_count)]))
            music_spec_db.append(10.0 * np.log10(abs(music_spec)))

            if len(storagefiles) == len(E_n):
                store(music_spec_db[len(music_spec_db) - 1], storagefiles[packet])
                eprint("Storing into: ", storagefiles[packet])

        pool.close()


        # Search for peaks in the spectrum and obtain their AoA and ToF
        packet_peaks = searchPeaks(music_spec_db[packet], theta, tau, computed_paths[packet])
        peaks.append(packet_peaks)

        if len(storagefiles) == len(E_n):
            save_peaks(packet_peaks, storagefiles[packet])


    # Cluster peaks
    flat_peaks = reduce(lambda x, y: x+y, peaks)
    filtered_peaks, removed_peaks = filterIndividualPoints(flat_peaks)
    filtered_peaks, rp = filterSpread(filtered_peaks)
    removed_peaks.extend(rp)
    cluster_means, cluster_covariances, weight_per_mean, height_per_mean = clusterGMM(filtered_peaks) # weight is the sum of probabilities, not normalized!

    likelihood_per_mean = []
    for i in range(len(cluster_means)):
        likelihood = 0

        likelihood += CLUSTER_WEIGHT_POINTS * (weight_per_mean[i] / len(filtered_peaks))
        likelihood -= CLUSTER_WEIGHT_THETA_VAR * (cluster_covariances[i][0][0]) # the diagonal entries of the covariance matrix are the variances
        likelihood -= CLUSTER_WEIGHT_TAU_VAR * (cluster_covariances[i][1][1])
        likelihood -= CLUSTER_WEIGHT_SMALL_TOF * (cluster_means[i][1])

        likelihood_per_mean.append(np.exp(likelihood))
        eprint("theta: %.2f" % np.degrees(cluster_means[i][0]))
        eprint("points: %.2f, theta_var: %.2f, tau_var: %.2f, tof: %.2f" % (CLUSTER_WEIGHT_POINTS * (weight_per_mean[i] / len(filtered_peaks)), CLUSTER_WEIGHT_THETA_VAR * (cluster_covariances[i][0][0]), CLUSTER_WEIGHT_TAU_VAR * (cluster_covariances[i][1][1]), CLUSTER_WEIGHT_SMALL_TOF * (cluster_means[i][1])))
        eprint("likelihood: %.2f, exp: %f\n" % (likelihood, np.exp(likelihood)))

    # TODO: filter likelihoods
    # TODO: combine likelihoods (180 degree, smiliar angles)

    sys.stderr.flush()

    # ---------------------
    # MUSIC Spectrum Plot:
    # ---------------------
    figure = plt.figure()
    mplot3d # the sole purpose of this is to prevent the mplot3d import from being optimized away by my IDE
    axes = plt.axes(projection='3d')
    # axes.set_title('calibration %d' % calibration_possibility)
    axes.set_xlabel('theta (degree)')
    axes.set_ylabel('tau (meter)')
    music_spec_db = music_spec_db[0].swapaxes(0, 1) # Yep, this library is weird
    x_grid, y_grid = np.meshgrid(theta / np.pi * 180.0, tau * speed_of_light)
    # axes.view_init(elev=45, azim=-45)
    axes.view_init(elev=85, azim=270)

    # Plot spectrum surface:
    # axes.set_xlim([-180, 180])
    # axes.set_ylim([-20, 20])
    # axes.plot_surface(x_grid, y_grid, music_spec_db, cmap=cm.get_cmap('winter'), cstride=1, rstride=1, linewidth=0)

    # Plot peaks
    axes.set_xlim([-180, 180])
    axes.set_ylim([-20, 20]) # TODO: <=12m
    filtered_peaks = np.array(filtered_peaks)
    removed_peaks = np.array(removed_peaks)
    axes.scatter(removed_peaks[:, 0] * 180.0 / np.pi, removed_peaks[:, 1] * speed_of_light, removed_peaks[:, 2] + 0.15, c='#AAAAAA', s=4.0)
    axes.scatter(filtered_peaks[:, 0] * 180.0 / np.pi, filtered_peaks[:, 1] * speed_of_light, filtered_peaks[:, 2] + 0.15, c='#FF0000')

    # Plot clusters
    # average_height = sum(np.array(filtered_peaks)[:, 2]) / len(filtered_peaks)
    # cluster_means = np.array(cluster_means)
    # for i in range(len(cluster_means)):
    #     axes.scatter(cluster_means[i, 0] * 180.0 / np.pi, cluster_means[i, 1] * speed_of_light, height_per_mean[i], c='#00FF00', s=(max(3.0, np.log(likelihood_per_mean[i])) * 4.0))

    mng = plt.get_current_fig_manager()
    mng.resize(*mng.window.maxsize())
    plt.show()

    # plt.plot(theta, music_spec[:, 0])
    # plt.show()



    # ---------------------
    # Sanitized CSI Plot:
    # ---------------------
    # plt.title("sanitized phase")
    # plt.xlabel("subcarrier")
    # plt.ylabel("Phase")
    #
    # x = range(0, SUBCARRIERS_USED)
    # for antenna in range(0, 3):
    #     y = [cmath.phase(x) for x in sanitized_csi[0][antenna]]
    #     plt.plot(x, y, color='blue', label=("S%d" % (antenna)))
    #     y = [cmath.phase(x) for x in scaled_csi[0][antenna]]
    #     plt.plot(x, y, color='grey', label=("O%d" % (antenna)))
    # plt.show()

    # Print sanitized phase for packet 0
    # for antenna in range(0, 3):
    #     coordsRawString = []
    #     coordsSanString = []
    #     for x in range(0, SUBCARRIERS_USED):
    #         coordsSanString.append("(%f, %f) " % (x, cmath.phase(sanitized_csi[0][antenna][x])))
    #         coordsRawString.append("(%f, %f) " % (x, cmath.phase(scaled_csi[0][antenna][x])))
    #     eprint("Antenna: ", antenna)
    #     eprint("".join(coordsRawString))
    #     eprint("".join(coordsSanString))

# based on https://github.com/yuehanlyu/Wifi-Localization/blob/zhaoxin/spotfi_algorithms.py#L103
def searchPeaks(spectrum, xValues, yValues, count): # TODO: better solution?
    neighbourhood = morphology.generate_binary_structure(2, 2) # TODO: larger neighbourhood?
    local_maximum = maximum_filter(spectrum, footprint=neighbourhood) == spectrum
    background = spectrum == 0
    background = morphology.binary_erosion(background, structure=neighbourhood, border_value=1)

    peak_mask = local_maximum ^ background
    peaks = []
    for x in range(spectrum.shape[0]):
        for y in range(spectrum.shape[1]):
            if x == 0 or y == 0 or x == spectrum.shape[0]-1 or y == spectrum.shape[1]-1: # TODO: maybe remove again
                continue

            if peak_mask[x][y] == True:
                peaks.append(np.array((xValues[x], yValues[y], spectrum[x][y])))

    list.sort(peaks, key=lambda e: e[2])
    peaks = peaks[::-1]
    return peaks[0:count]

# based on:
# https://github.com/Challenger1132/SpotFi/blob/master/joey-spotfi-simulation/noise_space_eigenvectors.m#L17
# https://pdfs.semanticscholar.org/5ff7/806b44e60d41c21429e1ad2755d72bba41d7.pdf
def constructNoiseSubspaceMatrix(X):
    X_H = X.conj().T   #  = X.H
    mat = X.dot(X_H)

    # Calculate right hand eigenvalues/vectors
    # eigvec is a column matrix of eigenvectors, eigvec[:, i] is the eigenvector corresponding to eigval[i]
    eigval, eigvec = linalg.eig(mat)

    # Normalize eigenvalues, use real part (matlab comparision just uses real part)
    max_eigval_real = reduce(lambda acc, e: acc if acc > np.real(e) else np.real(e), eigval)
    eigval = eigval / max_eigval_real

    # Assume 10 multipaths at max, check 10 largest
    # Sort eigvec, eigval
    sorted_ids = (-eigval).argsort()[::-1] # TODO: remove invert
    eigval = eigval[sorted_ids]
    eigvec = eigvec[:, sorted_ids]

    highest_index = len(eigval) - 2 # exclude first decrease
    lowest_index = len(eigval) - 10

    decrease_ratios = np.zeros(highest_index - lowest_index + 1)
    j = 0
    for i in range(highest_index, lowest_index, -1):
        decrease_ratios[j] = np.real(eigval[i + 1]) / np.real(eigval[i])
        j += 1

    max_decrease_index = np.argmax(decrease_ratios)
    eigval_index = len(eigval) - max_decrease_index
    computed_paths = max_decrease_index + 1
    eprint("Decrease ratios:", decrease_ratios)
    eprint("Computed paths:", computed_paths)

    E_n = eigvec[:, 0:(len(eigval) - computed_paths)]

    return computed_paths, E_n

def smoothCSI(csi_matrix):
    # TODO: adapt to SUBCARRIERS_USED
    smoothed_csi_matrix_12 = [np.concatenate((csi_matrix[0][i:i+16],
                                              csi_matrix[1][i:i+16]), axis=None) for i in range(15)]
    smoothed_csi_matrix_23 = [np.concatenate((csi_matrix[1][i:i+16],
                                              csi_matrix[2][i:i+16]), axis=None) for i in range(15)]
    smoothed_csi_matrix = np.concatenate((smoothed_csi_matrix_12,
                                          smoothed_csi_matrix_23), axis=0)

    return smoothed_csi_matrix

# SpotFi algorithm 1
# based on https://github.com/yuehanlyu/Wifi-Localization/blob/master/spotfi_algorithms.py#L28
def sanitizeToF(csi_matrix):
    # Unwrap csi ( -> continuous, no jumps between -PI and PI)
    phase_matrix = np.vstack((
        np.unwrap(np.array(subcarrierListToPhase(csi_matrix[0]))),
        np.unwrap(np.array(subcarrierListToPhase(csi_matrix[1]))),
        np.unwrap(np.array(subcarrierListToPhase(csi_matrix[2]))),
    ))

    # Minimize sum of (phaseMN + 2*PI*freq_delta*(n-1)*roh + beta)^2 over all M antennas, N subcarriers
    # If 2*PI*freq_delta is left out, the returned parameter will be scaled by a factor of 2*PI*freq_delta,
    # which cancels out in the second step of the algorithm, therefore this is omitted
    # numpy.polyfit minimizes the sum of quadratic deviations from the support points
    phases = np.concatenate((phase_matrix[0], phase_matrix[1], phase_matrix[2]))
    lin = np.linspace(1, SUBCARRIERS_USED, SUBCARRIERS_USED)
    x = np.concatenate((lin, lin, lin))

    coefficients = np.polyfit(x, phases, 1)
    tau = coefficients[0]   # coefficient of x^1 as x = n = antenna and roh is located there in the term

    # Subtract (subcarrier * tau) instead of add as our tau is scaled by 2*PI*freq_delta * (-1) as polyfit optimizes the difference but
    # the spotfi alg 1 optimizes the sum
    exp_phase_matrix = np.zeros((3, SUBCARRIERS_USED), dtype=np.complex)
    for antenna in range(0 ,3):
        for subcarrier in range(0, SUBCARRIERS_USED):
            new_phase = phase_matrix[antenna][subcarrier] - subcarrier * tau   # subcarrier = n
            exp_phase_matrix[antenna][subcarrier] = np.exp(complex(0, new_phase)) # get complex rotation according to new phase

    sanitized_csi = np.multiply(abs(np.array(csi_matrix)), exp_phase_matrix)   # (element wise)
    # sanitized_csi = exp_phase_matrix

    return sanitized_csi

def subcarrierListToPhase(l):
    return list(map(cmath.phase, l))


# Tests csi smoothing by handing in ids corresponding to the antenna and subcarrier of the csi info and verifying
# the resulting smoothed matrix by comparing it to the SpotFi paper
#TODO move to unit test?
def testSmoothCsi():
    mat = np.vstack((np.linspace(101,130,30), np.linspace(201,230,30), np.linspace(301,330,30)))
    smoothed = smoothCSI(mat)
    eprint(smoothed)



# For parallelization
def musicSpectrumFuncRange(thetaRange, tauRange, E_n, E_nH):
    if platform.system() == "Linux":
        os.sched_setaffinity(0, range(4))
    return [musicSpectrumFuncTauRange(thetaRange[x], tauRange, E_n, E_nH) for x in range(len(thetaRange))]

# For parallelization
def musicSpectrumFuncTauRange(theta, tauRange, E_n, E_nH):
    return [musicSpectrumFunc(theta, tauRange[y], E_n, E_nH) for y in range(len(tauRange))]

def musicSpectrumFunc(theta, tau, E_n, E_nH):
    steering_vector = steeringVector(theta, tau)

    res = E_nH.dot(steering_vector)
    res = E_n.dot(res)
    res = steering_vector.conj().T.dot(res)

    return complex(1, 0) / res
    # return complex(1, 0) / (steering_vector.conj().T * E_n * E_n.conj().T * steering_vector)

# Phase shift at the second antenna due to angle of arrival, exponentiate to get first->third, first->fourth, ... antenna
# phi(theta) = e^(-j*2*PI*d*sin(theta)*f/c)    (SpotFi paper page 272)
#
# Notes:
# antennaSpacing, frequency, ... in SI units
# frequency difference due to subcarrier "should" be negligible
# speed of radio in air is about the same as in vacuum (opposing to e.g. light)
def phaseShiftDueToAoA(theta):
    return np.exp(complex(0, -2.0 * np.pi * ANTENNA_SPACING * np.sin(theta) * CHANNEL_FREQUENCY / speed_of_light))   # todo radians vs degrees?
    # TODO: use subcarrier frequency instead of channel frequency?

# Phase shift due to time of flight between to adjacent subcarriers, exponentiate to get shift between more subcarriers
# omega(tau) = e^(-j*2*PI*f_d*tau)    (SpotFi paper page 273)
def phaseShiftDueToToF(tau):
    return np.exp(complex(0, -2.0 * np.pi * SUBCARRIER_FREQ_SPACING * tau))   # todo radians vs degrees?

# Steering vector (phase shift due to AoA and ToF over all subcarriers)
# This is obtained from the paper page 274, corresponding to one column of the steering matrix shown in Figure3, just with more subcarriers
def steeringVector(theta, tau):
    assert SUBCARRIERS_USED % 2 == 0, "Number of subcarriers needs to be divisible by 2"

    # combining 15 subcarriers over 2 antennas
    steering_vector = []
    for antenna in range(0, 2):
        for subcarrier in range(0, int(SUBCARRIERS_USED / 2)):
            steering_vector.append(
                np.power(phaseShiftDueToAoA(theta), antenna) * np.power(phaseShiftDueToToF(tau), subcarrier)
            )

    return np.array(steering_vector)





calibration_possibility = int(sys.stdin.readline())
lookupfiles = json.loads(sys.stdin.readline())
storagefiles = json.loads(sys.stdin.readline())
csi = json.loads(sys.stdin.readline())
spotfi(csi, calibration_possibility, lookupfiles, storagefiles)
