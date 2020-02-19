import json
import numpy as np
from matplotlib import pyplot as plt
from os import listdir
from os.path import isfile, join
from scipy.constants import speed_of_light


replays = [

]

for angle in range(-90, 91, 20):
    replay_clazz = []
    for dist in range(1, 5):
        replay_clazz.append("los%02ddeg_dist%d" % (angle, dist))
        replay_clazz.append("2los%02ddeg_dist%d" % (angle, dist))
    replays.append(replay_clazz)

print("WARNING, MISSING ONE REPLAY")


CLASS_LENGTH = 8
CLASSES = 10
assert len(replays) == CLASSES, "Not enough / too many replays found: %d,  expected: %d"
CALIBRATION = 1  # TODO: TRY OTHER CALIBRATIONS

all_peaks = []

print("Loading replay files...")
for replay_clazz in replays:
    assert len(replay_clazz) == CLASS_LENGTH, "Not enough replays in class"
    class_peaks = []

    for replay_file in replay_clazz:
        peaks_before = len(class_peaks)
        for packet_file in listdir(replay_file):
            if not packet_file.endswith("-%d.peaks" % CALIBRATION):
                continue

            with open(join(replay_file, packet_file), "r") as f:
                obj = json.loads(f.read())
                class_peaks.extend(obj["peaks"])
        print("Found %d peaks in %s" % (len(class_peaks) - peaks_before, replay_file))

    all_peaks.append(class_peaks)

all_peaks = [np.array([np.array(peak) for peak in clazz]) for clazz in all_peaks]
# all_peaks = np.array(all_peaks)

assert(len(all_peaks) == CLASSES), "Peak classes length doesn't equal expected classes"


# Generate image
aoas = 92
tofs = 100
peaks_per_pixel = [[np.zeros(CLASSES) for tof in range(0, tofs)] for aoa in range(0, aoas)]

for clazz in range(len(all_peaks)):
    for peak in all_peaks[clazz]:
        x = (peak[0] + np.pi) / (2*np.pi)
        y = (peak[1] + 20.0 / speed_of_light) / (2*20.0/speed_of_light)
        x = int(x * aoas)
        y = int(y * tofs)
        peaks_per_pixel[x][y][clazz] += 1

print(peaks_per_pixel)

# Plotting
plt.xlim(-np.pi,np.pi)
plt.ylim(-20.0 / speed_of_light, 20.0 / speed_of_light)

r = 0
b = 255
g = 0
for clazz in all_peaks:
    if(len(clazz) == 0):
        continue

    x = clazz[:, 0]
    y = clazz[:, 1]
    # plt.scatter(x, y, c="#%02x%02x%02x" % (int(r), int(g), int(b)))
    plt.scatter(x, y)
    r += 255 / 10
    b -= 255 / 10

plt.show()