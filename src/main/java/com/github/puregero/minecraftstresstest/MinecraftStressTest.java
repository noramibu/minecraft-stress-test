package com.github.puregero.minecraftstresstest;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MinecraftStressTest {

    private static final String ADDRESS = System.getProperty("bot.ip", "127.0.0.1");
    private static final int PORT = Integer.parseInt(System.getProperty("bot.port", "25565"));
    private static final double CENTER_X = Double.parseDouble(System.getProperty("bot.x", "0"));
    private static final double CENTER_Z = Double.parseDouble(System.getProperty("bot.z", "0"));
    public static final String DEFAULT_BOT_NAME = "TreasBot";
    public static final String DEFAULT_JOIN_TELEPORT_DELAY_MS = "10000";
    public static int JOIN_TELEPORT_DELAY_MS = Integer.parseInt(System.getProperty("bot.join.teleport.delay.ms", DEFAULT_JOIN_TELEPORT_DELAY_MS));
    public static final String DEFAULT_SPREAD_DISTANCE = "256";
    public static final String DEFAULT_SPREAD_COMMAND_DELAY_MS = "100";
    public static int SPREAD_COMMAND_DELAY_MS = Integer.parseInt(System.getProperty("bot.spread.command.delay.ms", DEFAULT_SPREAD_COMMAND_DELAY_MS));
    public static final String DEFAULT_TEAM_SIZE = "10";
    public static final String DEFAULT_TEAM_GAP = "25";
    public static final String DEFAULT_DELAY_BETWEEN_BOTS_MS = "100";
    public static int DELAY_BETWEEN_BOTS_MS = Integer.parseInt(System.getProperty("bot.login.delay.ms", DEFAULT_DELAY_BETWEEN_BOTS_MS));
    public static final String DEFAULT_RECONNECT_DELAY_MS = "30000";
    public static int RECONNECT_DELAY_MS = Integer.parseInt(System.getProperty("bot.reconnect.delay.ms", DEFAULT_RECONNECT_DELAY_MS));
    public static final String DEFAULT_AUTO_COUNT_INTERVAL_MS = "60000";

    public static final String DEFAULT_BOT_COUNT = "1";
    public static int BOT_COUNT = Integer.parseInt(System.getProperty("bot.count", DEFAULT_BOT_COUNT));

    private static final List<Bot> bots = new ArrayList<>();
    private static final Lock botsLock = new ReentrantLock();
    private static final AtomicBoolean addingBots = new AtomicBoolean();
    private static final AtomicInteger autoCountGeneration = new AtomicInteger();
    private static volatile boolean spreadEnabled = false;
    private static volatile double activeSpreadDistance = Double.parseDouble(System.getProperty("bot.spread.distance", DEFAULT_SPREAD_DISTANCE));
    private static volatile PlacementMode activePlacementMode = PlacementMode.SPREAD;
    private static volatile int activeTeamSize = Integer.parseInt(System.getProperty("bot.team.size", DEFAULT_TEAM_SIZE));
    private static volatile double activeTeamGap = Double.parseDouble(System.getProperty("bot.team.gap", DEFAULT_TEAM_GAP));
    private static volatile boolean autoCountEnabled = false;
    private static volatile int autoCountIncrement = 0;
    private static volatile long autoCountIntervalMs = Long.parseLong(
            System.getProperty("bot.autocount.interval.ms", DEFAULT_AUTO_COUNT_INTERVAL_MS));

    private static final EventLoopGroup workerGroup;
    private static final Class<? extends SocketChannel> nettyChannelClass;
    static {
        if (Epoll.isAvailable()) {
            workerGroup = new EpollEventLoopGroup();
            nettyChannelClass = EpollSocketChannel.class;
        } else if (KQueue.isAvailable()) {
            workerGroup = new KQueueEventLoopGroup();
            nettyChannelClass = KQueueSocketChannel.class;
        } else {
            workerGroup = new NioEventLoopGroup();
            nettyChannelClass = NioSocketChannel.class;
        }
        System.out.println("Using " + workerGroup.getClass().getSimpleName() + " with " + nettyChannelClass.getSimpleName() + " for network communication.");
    }

    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            printHelp();
            return;
        }

        updateBotCount();

        new CommandLine().run();

        System.out.println("stdin ended");
    }

    private static void printHelp() {
        System.out.println("Minecraft Stress Test");
        System.out.println("Usage: java [options] -jar minecraft-stress-test.jar");
        System.out.println("\nOptions:");
        System.out.println("  -Dbot.ip=<ip>                 Set the server IP (default: 127.0.0.1)");
        System.out.println("  -Dbot.port=<port>             Set the server port (default: 25565)");
        System.out.println("  -Dbot.count=<count>           Set the number of bots (default: 1)");
        System.out.println("  -Dbot.login.delay.ms=<delay>  Set the delay between bot logins in ms (default: 100)");
        System.out.println("  -Dbot.join.teleport.delay.ms=<delay> Set the delay before new bots teleport (default: 10000)");
        System.out.println("  -Dbot.reconnect.delay.ms=<delay> Set the delay before reconnecting disconnected bots (default: 30000)");
        System.out.println("  -Dbot.name=<name>             Set the base name for bots (default: TreasBot)");
        System.out.println("  -Dbot.x=<x>                   Set the center X coordinate (default: 0)");
        System.out.println("  -Dbot.z=<z>                   Set the center Z coordinate (default: 0)");
        System.out.println("  -Dbot.logs=<true|false>       Enable or disable bot logs (default: true)");
        System.out.println("  -Dbot.viewdistance=<distance> Set the view distance (default: 2)");
        System.out.println("  -Dbot.spread.distance=<dist>  Set the default spread distance (default: 256)");
        System.out.println("  -Dbot.spread.command.delay.ms=<delay> Set the delay between spread /tp commands (default: 100)");
        System.out.println("  -Dbot.team.size=<count>       Set the default team size (default: 10)");
        System.out.println("  -Dbot.team.gap=<gap>          Set the default gap inside each team (default: 25)");
        System.out.println("  -Dbot.autocount.interval.ms=<delay> Set the default autocount interval (default: 60000)");
        System.out.println("\nRuntime Commands:");
        System.out.println("  count <number>                Change the number of bots");
        System.out.println("  autocount <count> [time]      Add bots repeatedly, for example: autocount 100 60s");
        System.out.println("  autocount stop                Stop automatic count increases");
        System.out.println("  spread [distance]             Teleport bots or team centers using /tp");
        System.out.println("  team [count] [gap]            Teleport bots into teams using /tp");
        System.out.println("  spreaddelay <ms>              Change the delay between spread /tp commands");
        System.out.println("  logindelay <value>            Change the delay between bot logins");
        System.out.println("  joindelay <ms>                Change the delay before new bots teleport");
        System.out.println("  reconnectdelay <ms>           Change the reconnect delay");
        System.out.println("\nExample:");
        System.out.println("  java -Dbot.ip=localhost -Dbot.port=25565 -Dbot.count=10 -jar minecraft-stress-test.jar");
    }

    public static void updateBotCount() {
        removeBotsIfNeeded();
        addBotIfNeeded(true);
    }

    public static void scheduleReconnect(Bot bot) {
        CompletableFuture.delayedExecutor(RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS)
                .execute(() -> reconnectBot(bot));
    }

    public static void startAutoCount(int increment, long intervalMs) {
        if (increment <= 0 || intervalMs <= 0) {
            stopAutoCount();
            return;
        }

        autoCountIncrement = increment;
        autoCountIntervalMs = intervalMs;
        autoCountEnabled = true;

        int generation = autoCountGeneration.incrementAndGet();
        System.out.println("Auto count will add "
                + increment
                + " bots every "
                + intervalMs
                + "ms");
        scheduleAutoCount(generation);
    }

    public static void stopAutoCount() {
        autoCountEnabled = false;
        autoCountGeneration.incrementAndGet();
        System.out.println("Auto count stopped");
    }

    public static void spreadBots(double distance) {
        activeSpreadDistance = distance;
        spreadEnabled = true;

        List<Bot> botsSnapshot;
        botsLock.lock();
        try {
            botsSnapshot = List.copyOf(bots);
        } finally {
            botsLock.unlock();
        }

        if (botsSnapshot.isEmpty()) {
            System.out.println("No bots connected to spread. Future bots will spread after joining.");
            return;
        }

        scheduleTeleportCommands(botsSnapshot);

        if (activePlacementMode == PlacementMode.TEAM) {
            System.out.println("Scheduled team spread teleport commands for "
                    + botsSnapshot.size()
                    + " bots");
        } else {
            System.out.println("Scheduled spread teleport commands for " + botsSnapshot.size() + " bots");
        }
    }

    public static void teamBots(int teamSize, double teamGap) {
        activeTeamSize = teamSize;
        activeTeamGap = teamGap;
        activePlacementMode = PlacementMode.TEAM;
        spreadEnabled = true;

        List<Bot> botsSnapshot;
        botsLock.lock();
        try {
            botsSnapshot = List.copyOf(bots);
        } finally {
            botsLock.unlock();
        }

        if (botsSnapshot.isEmpty()) {
            System.out.println("No bots connected to team. Future bots will team after joining.");
            return;
        }

        scheduleTeleportCommands(botsSnapshot);

        int teams = (int) Math.ceil((double) botsSnapshot.size() / teamSize);
        System.out.println("Scheduled team teleport commands for "
                + botsSnapshot.size()
                + " bots in "
                + teams
                + " teams");
    }

    public static void scheduleSpreadForBot(Bot bot) {
        if (!spreadEnabled) {
            return;
        }

        int index = getBotIndex(bot);
        if (index < 0) {
            return;
        }

        CompletableFuture.delayedExecutor(JOIN_TELEPORT_DELAY_MS, TimeUnit.MILLISECONDS)
                .execute(() -> spreadBot(bot));
    }

    private static void spreadBot(Bot bot) {
        int index = getBotIndex(bot);
        if (index < 0) {
            return;
        }

        SpreadTarget target = getActiveTarget(index);
        bot.sendTeleportCommand(target.x(), target.z());
    }

    public static double getActiveSpreadDistance() {
        return activeSpreadDistance;
    }

    public static int getActiveTeamSize() {
        return activeTeamSize;
    }

    public static double getActiveTeamGap() {
        return activeTeamGap;
    }

    public static long getAutoCountIntervalMs() {
        return autoCountIntervalMs;
    }

    private static void scheduleTeleportCommands(List<Bot> botsSnapshot) {
        for (int i = 0; i < botsSnapshot.size(); i++) {
            Bot bot = botsSnapshot.get(i);
            SpreadTarget target = getActiveTarget(i);
            long delayMs = (long) i * SPREAD_COMMAND_DELAY_MS;
            CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                    .execute(() -> bot.sendTeleportCommand(target.x(), target.z()));
        }
    }

    private static void scheduleAutoCount(int generation) {
        CompletableFuture.delayedExecutor(autoCountIntervalMs, TimeUnit.MILLISECONDS)
                .execute(() -> runAutoCount(generation));
    }

    private static void runAutoCount(int generation) {
        if (!autoCountEnabled || generation != autoCountGeneration.get()) {
            return;
        }

        BOT_COUNT += autoCountIncrement;
        System.out.println("Auto count increased bot count to " + BOT_COUNT);
        updateBotCount();
        scheduleAutoCount(generation);
    }

    private static int getBotIndex(Bot bot) {
        botsLock.lock();
        try {
            return bots.indexOf(bot);
        } finally {
            botsLock.unlock();
        }
    }

    private static SpreadTarget getActiveTarget(int index) {
        if (activePlacementMode == PlacementMode.TEAM) {
            return getTeamTarget(index);
        }

        return getSpreadTarget(index, activeSpreadDistance);
    }

    private static SpreadTarget getTeamTarget(int index) {
        int teamIndex = index / activeTeamSize;
        int memberIndex = index % activeTeamSize;
        SpreadTarget teamCenter = getSpreadTarget(teamIndex, activeSpreadDistance);
        SpreadTarget memberOffset = getSpreadTarget(memberIndex, activeTeamGap, 0, 0);

        return new SpreadTarget(teamCenter.x() + memberOffset.x(), teamCenter.z() + memberOffset.z());
    }

    private static SpreadTarget getSpreadTarget(int index, double distance) {
        return getSpreadTarget(index, distance, CENTER_X, CENTER_Z);
    }

    private static SpreadTarget getSpreadTarget(int index, double distance, double centerX, double centerZ) {
        if (index == 0) {
            return new SpreadTarget(centerX, centerZ);
        }

        int ring = (int) Math.ceil((Math.sqrt(index + 1) - 1.0) / 2.0);
        int sideLength = ring * 2;
        int firstIndexInRing = (2 * ring - 1) * (2 * ring - 1);
        int offset = index - firstIndexInRing;
        int side = offset / sideLength;
        int position = offset % sideLength;
        int gridX;
        int gridZ;

        if (side == 0) {
            gridX = ring;
            gridZ = -ring + 1 + position;
        } else if (side == 1) {
            gridX = ring - 1 - position;
            gridZ = ring;
        } else if (side == 2) {
            gridX = -ring;
            gridZ = ring - 1 - position;
        } else {
            gridX = -ring + 1 + position;
            gridZ = -ring;
        }

        return new SpreadTarget(centerX + gridX * distance, centerZ + gridZ * distance);
    }

    private enum PlacementMode {
        SPREAD,
        TEAM
    }

    private record SpreadTarget(double x, double z) {
    }

    private static void removeBotsIfNeeded() {
        botsLock.lock();
        try {
            Bot removedBot;
            while (bots.size() > BOT_COUNT) {
                removedBot = bots.remove(bots.size() - 1);
                removedBot.close();
            }
        } finally {
            botsLock.unlock();
        }
    }

    private static void reconnectBot(Bot bot) {
        String name = bot.getUsername();
        botsLock.lock();
        try {
            int index = bots.indexOf(bot);
            if (index < 0 || index >= BOT_COUNT) {
                return;
            }

            System.out.println("Reconnecting " + name);
            bots.set(index, connectBot(name, ADDRESS, PORT));
        } finally {
            botsLock.unlock();
        }
    }

    private static void addBotIfNeeded(boolean firstCall) {
        if (!firstCall || !addingBots.getAndSet(true)) {
            boolean scheduledNextCall = false;
            try {
                botsLock.lock();
                try {
                    if (bots.size() < BOT_COUNT) {
                        bots.add(connectBot(
                                System.getProperty("bot.name", DEFAULT_BOT_NAME) + (bots.size() + 1),
                                ADDRESS,
                                PORT));
                        CompletableFuture.delayedExecutor(DELAY_BETWEEN_BOTS_MS, TimeUnit.MILLISECONDS).execute(() -> addBotIfNeeded(false));
                        scheduledNextCall = true;
                    }
                } finally {
                    botsLock.unlock();
                }
            } finally {
                if (!scheduledNextCall) {
                    addingBots.set(false);
                }
            }
        }
    }

    private static Bot connectBot(String name, String address, int port) {
        Bot bot = new Bot(name, address, port);

        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(nettyChannelClass);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                bot.channel = ch;
                ch.pipeline().addLast("packetEncoder", new PacketEncoder());
                ch.pipeline().addLast("packetDecoder", new PacketDecoder());
                ch.pipeline().addLast("bot", bot);
            }
        });

        b.connect(address, port);

        return bot;
    }

}
