package com.github.puregero.minecraftstresstest;

import java.util.Scanner;

public class CommandLine implements Runnable {

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNext()) {
            String line = scanner.nextLine().trim();
            String[] args = line.isEmpty() ? new String[0] : line.split("\\s+");

            try {
                if (args.length == 0) {
                    printCommands();
                    continue;
                }

                if (args[0].equalsIgnoreCase("count") || args[0].equalsIgnoreCase("botcount")) {
                    int botCount = Math.max(0, Integer.parseInt(args[1]));
                    System.out.println("Setting bot count to " + botCount);
                    MinecraftStressTest.BOT_COUNT = botCount;
                    MinecraftStressTest.updateBotCount();
                } else if (args[0].equalsIgnoreCase("autocount")) {
                    if (args.length > 1 && isStopCommand(args[1])) {
                        MinecraftStressTest.stopAutoCount();
                        continue;
                    }
                    if (args.length < 2) {
                        printCommands();
                        continue;
                    }

                    int increment = Math.max(0, Integer.parseInt(args[1]));
                    long intervalMs = args.length > 2
                            ? Math.max(0L, parseDurationMs(args[2]))
                            : MinecraftStressTest.getAutoCountIntervalMs();
                    MinecraftStressTest.startAutoCount(increment, intervalMs);
                } else if (args[0].equalsIgnoreCase("spread")) {
                    double distance = args.length > 1
                            ? Math.max(0.0, Double.parseDouble(args[1]))
                            : MinecraftStressTest.getActiveSpreadDistance();
                    System.out.println("Setting spread distance to " + distance + " blocks");
                    MinecraftStressTest.spreadBots(distance);
                } else if (args[0].equalsIgnoreCase("team")) {
                    int teamSize = args.length > 1
                            ? Math.max(1, Integer.parseInt(args[1]))
                            : MinecraftStressTest.getActiveTeamSize();
                    double teamGap = args.length > 2
                            ? Math.max(0.0, Double.parseDouble(args[2]))
                            : MinecraftStressTest.getActiveTeamGap();
                    System.out.println("Teaming bots into groups of "
                            + teamSize
                            + " with "
                            + teamGap
                            + " blocks between members");
                    MinecraftStressTest.teamBots(teamSize, teamGap);
                } else if (args[0].equalsIgnoreCase("logindelay")) {
                    int loginDelay = Math.max(0, Integer.parseInt(args[1]));
                    System.out.println("Setting login delay to " + loginDelay);
                    MinecraftStressTest.DELAY_BETWEEN_BOTS_MS = loginDelay;
                } else if (args[0].equalsIgnoreCase("joindelay")) {
                    int joinDelay = Math.max(0, Integer.parseInt(args[1]));
                    System.out.println("Setting join teleport delay to " + joinDelay);
                    MinecraftStressTest.JOIN_TELEPORT_DELAY_MS = joinDelay;
                } else if (args[0].equalsIgnoreCase("reconnectdelay")) {
                    int reconnectDelay = Math.max(0, Integer.parseInt(args[1]));
                    System.out.println("Setting reconnect delay to " + reconnectDelay);
                    MinecraftStressTest.RECONNECT_DELAY_MS = reconnectDelay;
                } else if (args[0].equalsIgnoreCase("spreaddelay")) {
                    int spreadDelay = Math.max(0, Integer.parseInt(args[1]));
                    System.out.println("Setting spread command delay to " + spreadDelay);
                    MinecraftStressTest.SPREAD_COMMAND_DELAY_MS = spreadDelay;
                } else {
                    printCommands();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void printCommands() {
        System.out.println("Commands:");
        System.out.println("count <number of bots>   (Default: " + MinecraftStressTest.DEFAULT_BOT_COUNT + ")");
        System.out.println("autocount <count> [time] (Example: autocount 100 60s)");
        System.out.println("autocount stop           Stop automatic count increases");
        System.out.println("spread [distance]        Set bot or team-center spacing (Default: "
                + MinecraftStressTest.DEFAULT_SPREAD_DISTANCE
                + ")");
        System.out.println("team [count] [gap]       (Default: "
                + MinecraftStressTest.DEFAULT_TEAM_SIZE
                + " "
                + MinecraftStressTest.DEFAULT_TEAM_GAP
                + ")");
        System.out.println("spreaddelay <ms>         (Default: " + MinecraftStressTest.DEFAULT_SPREAD_COMMAND_DELAY_MS + ")");
        System.out.println("logindelay <value>       (Default: " + MinecraftStressTest.DEFAULT_DELAY_BETWEEN_BOTS_MS + ")");
        System.out.println("joindelay <value>        (Default: " + MinecraftStressTest.DEFAULT_JOIN_TELEPORT_DELAY_MS + ")");
        System.out.println("reconnectdelay <value>   (Default: " + MinecraftStressTest.DEFAULT_RECONNECT_DELAY_MS + ")");
    }

    private boolean isStopCommand(String value) {
        return value.equalsIgnoreCase("stop")
                || value.equalsIgnoreCase("off")
                || value.equalsIgnoreCase("disable");
    }

    private long parseDurationMs(String value) {
        String lowerValue = value.toLowerCase();
        if (lowerValue.endsWith("ms")) {
            return Long.parseLong(lowerValue.substring(0, lowerValue.length() - 2));
        }
        if (lowerValue.endsWith("s")) {
            return Long.parseLong(lowerValue.substring(0, lowerValue.length() - 1)) * 1000L;
        }
        if (lowerValue.endsWith("m")) {
            return Long.parseLong(lowerValue.substring(0, lowerValue.length() - 1)) * 60_000L;
        }
        if (lowerValue.endsWith("h")) {
            return Long.parseLong(lowerValue.substring(0, lowerValue.length() - 1)) * 3_600_000L;
        }

        return Long.parseLong(lowerValue);
    }
}
