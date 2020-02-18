package de.putterer.indloc;

import de.putterer.indloc.csi.CSIRecording;
import de.putterer.indloc.csi.CSIReplay;
import de.putterer.indloc.csi.CSITesting;
import de.putterer.indloc.rssi.RSSITrilateration;
import de.putterer.indloc.util.ArgumentParser;
import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.Serialization;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class IndoorLocalization {

    public static void main(String args[]) throws IOException, ClassNotFoundException {
        if(args.length == 0) {
            help();
            return;
        }

        Map<String, String> arguments = ArgumentParser.parse(Arrays.copyOfRange(args, 1, args.length));
        String cmd = args[0];
        List<String> newArgs = new ArrayList<>();

        if(cmd.equals("help")) {
            help();
        }

        if(arguments.containsKey("room")) {
            Logger.info("Loading room %s...", arguments.get("room"));
            Config.ROOM = Serialization.deserialize(Paths.get(arguments.get("room")), Config.RoomConfig.class);
        }

        Logger.setLogLevel(Logger.Level.getByName(arguments.getOrDefault("log-level", "DEBUG")));

        if(cmd.equals("recordcsi")) {
            newArgs.add(arguments.get("path"));
            if(arguments.containsKey("payloadlen")) {
                newArgs.add(arguments.get("payloadlen"));
            }
            CSIRecording.main(newArgs.toArray(new String[newArgs.size()]));
        }

        if(cmd.equals("replaycsi")) {
            newArgs.add(arguments.get("path"));
            newArgs.add(String.valueOf(arguments.containsKey("spotfi"))); // TODO add support for store / load
            newArgs.add(arguments.getOrDefault("--group", "1"));
            newArgs.add(arguments.getOrDefault("preview", ""));
            newArgs.add(arguments.getOrDefault("previewRX", "3"));
            newArgs.add(arguments.getOrDefault("previewTX", "1"));
            CSIReplay.main(newArgs.toArray(new String[newArgs.size()]));
        }

        if(cmd.equals("csitesting")) {
            CSITesting.main(new String[]{});
        }

        if(cmd.equals("rssitri")) {
            RSSITrilateration.main(new String[]{});
        }

        if(cmd.equals("uidemo")) {
            UserInterface.main(new String[]{});
        }
    }

    private static void help() {
        System.out.println("Usage:                  (set log level with --log-level)\n" +
                "help                               produce help message\n\n" +
                "recordcsi                          Starts recording csi\n" +
                "    --path [path]                  the directory to save the recording in\n" +
                "   (--payloadlen [l])              optional, filter recording for payload length\n" +
                "   (--room [room])                 optional, load room from file\n\n" +
                "replaycsi                          starts the replay (loads \"path/room.cfg\" as well)\n" +
                "    --path [path]                  the directory the recording is located in\n" +
                "   (--spotfi)                      run the spotfi algorithm defined in \"spotfi.py\" on every csi\n" +
                "   (--group [threshold])           release packets as a group after the threshold has been reached\n" +
                "   (--preview [apc])               activate preview, a: amplitude, p: phase, c: plot csi\n" +
                "   (--previewRX [antennas])        number of rx antennas to show in the previews (default: 3)\n" +
                "   (--previewTX [antennas])        number of tx antennas to show in the previews (default: 1)\n\n" +
                "csitesting (--room [room])         starts the csi testing application\n" +
                "rssitri (--room [room])            starts trilateration based on rssi\n" +
                "uidemo (--room [room])             opens a test window of the trilateration user interface");
    }

}
