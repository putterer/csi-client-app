import json
import os.path
import numpy as np

# def store(music_spec, file):
#     f = open(file, "wb+")
#     list = [[y for y in x] for x in music_spec]
#     f.write(pickle.dumps(list))
#     f.close()
#
# def lookup(file):
#     if not os.path.exists(file):
#         return None
#
#     f = open(file, "rb")
#
#     music_spec_db = pickle.loads(f.read())
#     return np.array(music_spec_db)

def store(music_spec, file):
    file = ("%s.spectrum" % file)
    f = open(file, "w+")
    obj = {
        "music_spec_db": [[y for y in x] for x in music_spec]
    }
    f.write(json.dumps(obj))
    f.close()

def lookup(file):
    file = ("%s.spectrum" % file)

    if not os.path.exists(file):
        return None

    f = open(file, "r")

    obj = json.loads(f.read())
    return np.array(obj["music_spec_db"])

def save_peaks(peaks, file):
    file = ("%s.peaks" % file)
    f = open(file, "w+")
    obj = {
        "peaks": [[v for v in peak] for peak in peaks]
    }
    f.write(json.dumps(obj))
    f.close()