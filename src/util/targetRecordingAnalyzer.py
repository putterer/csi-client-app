import matplotlib.pyplot as plt

file_list = [
    "../../propConst38.txt",
    "../../propConst39.txt",
    "../../propConst40.txt",
    "../../propConst41.txt",
    "../../propConst42.txt",
]

plot_data = []
for file in file_list:
    f = open(file, "r")
    lines = f.readlines()
    f.close()

    distanceStrings = [line.split("Distance: ")[1] for line in lines]
    distances = [float(distance.replace("\n", "")) for distance in distanceStrings]
    plot_data.append(distances)

pos = [1, 2, 3, 4, 5]
labels = [3.8, 3.9, 4.0, 4.1, 4.2]

means = [sum(distances) / len(distances) for distances in plot_data]

fig = plt.figure()
ax = plt.subplot(111)
plot = ax.violinplot(plot_data, pos, showmeans=True)
ax.set_xticks(pos)
ax.set_xticklabels(labels)
ax.set_ylabel("error in cm")
ax.set_xlabel("signal propagation constant e")
plt.grid(True, "major", axis='y')
# ax.scatter(pos, means)
plt.show()