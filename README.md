# CSI Client App

This application allows obtaining, processing, previewing, recording and replay CSI information sent by a router using the [Atheros CSI Tool](https://wands.sg/research/wifi/AtherosCSI/) in combination with my [OpenWRT CSI Server](https://github.com/putterer/csi-server-openwrt).

It was developed as part of my bachelor's thesis and therefore includes code about indoor localization as well as trilateration using RSSI.
The python implementation of the [SpotFi](https://web.stanford.edu/~skatti/pubs/sigcomm15-spotfi.pdf) algorithm was removed from this repository.

The config files specify location and properties of the stations within a room due to that.

## Building
The project uses Java 8+.

It does not use any build automation and dependency management tool like
Maven or Gradle as it is required to run and be modified on a system not connected
to the internet due to running an access point as well as connecting to a router using
Ethernet. Dependencies therefore need to be added manually. The used libraries are [Lombok](https://projectlombok.org/), [XChart](https://knowm.org/open-source/xchart/) and [Gson](https://github.com/google/gson).
The project can also be built as a self contained runnable JAR file using the `indloc.IndoorLocalization` class as an entry point which makes most tools accessible using a command line interface (CLI). When run, it will produce a help message:

```
Usage:                  (set log level with --log-level)
help                               produce help message

recordcsi                          Starts recording csi
    --path [path]                  the directory to save the recording in
   (--payloadlen [l])              optional, filter recording for payload length
   (--room [room])                 optional, load room from file

replaycsi                          starts the replay (loads "path/room.cfg" as well)
    --path [path]                  the directory the recording is located in
   (--spotfi)                      run the spotfi algorithm defined in "spotfi.py" on every csi
   (--group [threshold])           release packets as a group after the threshold has been reached
   (--preview [apc])               activate preview, a: amplitude, p: phase, c: plot csi
   (--previewRX [antennas])        number of rx antennas to show in the previews (default: 3)
   (--previewTX [antennas])        number of tx antennas to show in the previews (default: 1)

csitesting (--room [room])         starts the csi testing application
rssitri (--room [room])            starts trilateration based on rssi
uidemo (--room [room])             opens a test window of the trilateration user interface
```

## (Room) Configuration
The application uses a configuration for the current room which can be found in `indloc.Config` and stores information about the experimental setup like the used devices, their MAC addresses, their positions, RSSI distance estimators and internal CSI phase shift calibrations. This allows storing room configurations alongside recordings and applying them once a replay is loaded. This happens automatically for replays, but can be overridden for most tools by using the `--room` parameter and specifying the path to a `room.cfg` file.

```
{
    "width": 1000,
    "height": 2000,
    "stations":[
        {
            "HW_ADDRESS": "01:23:45:67:89:AB",
            "IP_ADDRESS": "192.168.0.200",
            "location": {"x":0.0,"y":0.0}
        }
    ]
}
```

## RSSI Trilateration
By running `indloc.rssi.RSSITrilateration` or using the CLI `rssitri` option, the trilateration functionality can be started. It will use the room configuration and start sending ICMP echo requests to the configured stations. It will then continuously obtain the RSSI values from the kernel and use them to calculate the estimated distance. Using this distance, a target location will be estimated. The program displays a Swing-based graphical representation of the station locations, estimated distances and target location. Each time the `R` key is pressed, the next target position (by index) and estimate as well as their distance will be written to a recording file.

## Recording CSI
By running `indloc.\-csi.CSIRecording` or using the CLI `recordcsi` option, a CSI recording can be started.

The app will subscribe to the configured station using the specified payload length as the filter option. All recorded CSI packets including timestamp, status and CSI matrix will be stored in the specified recording folder in the JSON format. The room configuration is saved there as well.

This application does not automatically start sending ICMP echo requests to the configured stations which is necessary for calculating CSI there.

## Replaying CSI
By running `indloc.csi.CSIReplay` or using the CLI `replaycsi` option, a CSI replay can be started.

The replay including the room configuration will be loaded from the specified folder. The program will start releasing CSI packets at the same intervals as they were captured during the recording. The interface supports grouping the packets till a specified threshold and then releasing them at once. This is useful when the SpotFi implementation is activated.

Using the `--spotfi` parameter, the SpotFi implementation can be activated and the CSI Client will run a python process with the `spotfi.py` file each time a group threshold is reached.

Calibration information is automatically applied based on MAC address in the replay if information for the station is present in `indloc.csi.calibration.PhaseOffset`. The python process is started four times in parallel, once for each possible calibration due to 180° shifts after startup.

The CLI also allows specifying a preview mode. Depending on which of the letters c, a or p the parameter contains, a preview of the plotted complex CSI (c), the amplitude across all subcarriers (a) or the phase across all subcarriers (p) will be activated. Using the `previewRX` and `previewTX` parameters, the number of antennas on each to be displayed in the previews can be selected.

## Real Time CSI
To preview and process CSI information in realtime, the code found in `indloc.csi.CSITesting` available by using the CLI `csitesting` option can be modified and used. This way CSI information received from the station can be investigated and the SpotFi/MUSIC algorithm be executed during runtime.