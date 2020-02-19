import numpy as np
import sys

def dbinv(x):
    return 10 ** (x / 10.)

## TODO: this is copy pasted
def get_scaled_csi(csi_status, csi_matrix):

    num_tones = csi_status['num_tones']
    nr = csi_status['nr']
    nc = csi_status['nc']
    csi = csi_matrix[0:nr, 0:nc, 0:num_tones]

    csi_squared = np.zeros((nr, nc, num_tones), dtype=complex)
    csi_scaled = np.zeros((nr, nc, num_tones), dtype=complex)
    for m in range(num_tones):
        for j in range (0, nc):
            for k in range (0, nr):
                csi_squared[k,j,m] = csi[k,j,m] * np.conjugate(csi[k,j,m])

    # TODO: euhm, what about a for loop?
    rssi_mag = 0
    if csi_status['rssi_0'] != 128:
        signal_level = int (csi_status['rssi_0']) - 95
        rssi_mag = dbinv(signal_level)
        csi_pwr = np.sum(csi_squared[0,:,:])
        csi_scaled[0,:,:] = 10*np.log10(csi_squared[0,:,:]/csi_pwr*rssi_mag)
    rssi_mag = 0
    if csi_status['rssi_0'] != 128:
        signal_level = int (csi_status['rssi_0']) - 95
        rssi_mag = dbinv(signal_level)
        csi_pwr = np.sum(csi_squared[1,:,:])
        csi_scaled[1,:,:] = 10*np.log10(csi_squared[1,:,:]/csi_pwr*rssi_mag)
    rssi_mag = 0
    if csi_status['rssi_0'] != 128:
        signal_level = int (csi_status['rssi_0']) - 95
        rssi_mag = dbinv(signal_level)
        csi_pwr = np.sum(csi_squared[2,:,:])
        csi_scaled[2,:,:] = 10*np.log10(csi_squared[2,:,:]/csi_pwr*rssi_mag)

    return csi_scaled


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)