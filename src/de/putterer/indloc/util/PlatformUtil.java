package de.putterer.indloc.util;

import java.io.IOException;

public class PlatformUtil {


    private static Boolean cachedI3 = null;
    public static boolean isRunningI3() {
        if(cachedI3 != null) {
            return cachedI3;
        }

        try {
            Process process = Runtime.getRuntime().exec("ps aux");
            String stdout = new String(process.getInputStream().readAllBytes());

            cachedI3 = stdout.contains("i3");
            return cachedI3;

        } catch (IOException e) {
            Logger.error("Error while obtaining window manager", e);
            cachedI3 = false;
            return false;
        }
    }

}
