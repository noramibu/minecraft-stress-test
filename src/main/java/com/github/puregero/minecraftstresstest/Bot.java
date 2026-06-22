package com.github.puregero.minecraftstresstest;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


public class Bot extends ChannelInboundHandlerAdapter {
    private static final int PROTOCOL_VERSION = Integer.parseInt(System.getProperty("bot.protocol.version", "775")); // 775 is 26.1 https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Protocol_version_numbers
    private static final boolean LOGS = Boolean.parseBoolean(System.getProperty("bot.logs", "true"));
    private static final int VIEW_DISTANCE = Integer.parseInt(System.getProperty("bot.viewdistance", "2"));
    private static final int RESOURCE_PACK_RESPONSE = Integer.parseInt(System.getProperty("bot.resource.pack.response", "3"));

    public SocketChannel channel;
    private String username;
    private final String address;
    private final int port;
    private UUID uuid;
    private boolean loginState = true;
    private boolean configState = false;
    private boolean playState = false;

    private double x = 0;
    private double y = 0;
    private double z = 0;

    private boolean isSpawned = false;
    private boolean scheduledSpread = false;

    public Bot(String username, String address, int port) {
        this.username = username;
        this.address = address;
        this.port = port;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        sendPacket(ctx, PacketIds.Serverbound.Handshaking.HANDSHAKE, buffer -> {
            buffer.writeVarInt(PROTOCOL_VERSION);
            buffer.writeUtf(address);
            buffer.writeShort(port);
            buffer.writeVarInt(2);
        });

        sendPacket(ctx, PacketIds.Serverbound.Login.LOGIN_START, buffer -> {
            buffer.writeUtf(username);
            buffer.writeUUID(UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)));
        });
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (uuid != null) {
            System.out.println(username + " has disconnected from " + address + ":" + port);
        }
        MinecraftStressTest.scheduleReconnect(this);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            FriendlyByteBuf byteBuf = new FriendlyByteBuf((ByteBuf) msg);
            if (loginState) {
                channelReadLogin(ctx, byteBuf);

            } else if (configState) {
                channelReadConfig(ctx, byteBuf);

            } else if (playState) {
                channelReadPlay(ctx, byteBuf);
            }
        } finally {
            ((ByteBuf) msg).release();
        }
    }


    private void channelReadLogin(ChannelHandlerContext ctx, FriendlyByteBuf byteBuf) {
        int packetId = byteBuf.readVarInt();

        if (packetId == PacketIds.Clientbound.Login.DISCONNECT) {
            System.out.println(username + " was disconnected during login due to " + byteBuf.readUtf());
            ctx.close();

        } else if (packetId == PacketIds.Clientbound.Login.ENCRYPTION_REQUEST) {
            System.out.println("Server requesting for ENCRYPTION_REQUEST, so it is on ONLINEMODE, disconnecting");
            ctx.close();

        } else if (packetId == PacketIds.Clientbound.Login.LOGIN_SUCCESS) {

            if (PROTOCOL_VERSION >= 764) {
                sendPacket(ctx, PacketIds.Serverbound.Login.LOGIN_ACKNOWLEDGED, buffer -> {
                });
            }

            loggedIn(ctx, byteBuf);

        } else if (packetId == PacketIds.Clientbound.Login.SET_COMPRESSION) {
            byteBuf.readVarInt();
            ctx.pipeline().addAfter("packetDecoder", "compressionDecoder", new CompressionDecoder());
            ctx.pipeline().addAfter("packetEncoder", "compressionEncoder", new CompressionEncoder());
        } else {
            throw new RuntimeException("Unknown login packet id of " + packetId);
        }
    }


    private void loggedIn(ChannelHandlerContext ctx, FriendlyByteBuf byteBuf) {
        UUID uuid = byteBuf.readUUID();
        String username = byteBuf.readUtf();
        int numberElements = byteBuf.readVarInt(); //number of elements after this position
        boolean isSigned = false;

        if (numberElements > 0) {
            try {
                byteBuf.readUtf(); //name
                byteBuf.readUtf(); //value
                isSigned = byteBuf.readBoolean(); //issigned
            } catch (Exception e) {
            }
        }

        this.uuid = uuid;
        this.username = username;

        if (isSigned) {
            System.out.println(username + " (" + uuid + ") has logged in on an ONLINEMODE server, stopping");
            ctx.close();
            return;
        } else
            System.out.println(username + " (" + uuid + ") has logged in");

        loginState = false;
        configState = true;
        //System.out.println("changing to config mode");

        CompletableFuture.delayedExecutor(1000, TimeUnit.MILLISECONDS).execute(() -> {
            if (configState) {
                sendPacket(ctx, PacketIds.Serverbound.Configuration.CLIENT_INFORMATION, buffer -> {
                    buffer.writeUtf("en_GB");
                    buffer.writeByte(VIEW_DISTANCE);
                    buffer.writeVarInt(0);
                    buffer.writeBoolean(true);
                    buffer.writeByte(0);
                    buffer.writeVarInt(0);
                    buffer.writeBoolean(false);
                    buffer.writeBoolean(true);
                    buffer.writeVarInt(0);
                });

                sendPacket(ctx, PacketIds.Serverbound.Configuration.KNOWN_PACKS, buffer -> {
                    buffer.writeVarInt(0);
                });
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!isConnectionReset(cause)) {
            cause.printStackTrace();
        }
        ctx.close();
    }

    private void channelReadConfig(ChannelHandlerContext ctx, FriendlyByteBuf byteBuf) {
        int packetId = byteBuf.readVarInt();

        if (packetId == PacketIds.Clientbound.Configuration.DISCONNECT) {
            System.out.println(username + " (" + uuid + ") (config) was kicked due to " + byteBuf.readUtf());
            ctx.close();

        } else if (packetId == PacketIds.Clientbound.Configuration.FINISH_CONFIGURATION) {

            sendPacket(ctx, PacketIds.Serverbound.Configuration.FINISH_CONFIGURATION, buffer -> {
            });

            configState = false;
            playState = true;
            scheduleSpreadIfNeeded();
            //System.out.println("changing to play mode");

        } else if (packetId == PacketIds.Clientbound.Configuration.KEEP_ALIVE) {
            long id = byteBuf.readLong();
            sendPacket(ctx, PacketIds.Serverbound.Configuration.KEEP_ALIVE, buffer -> buffer.writeLong(id));
            //System.out.println(username + " (" + uuid + ") keep alive config mode");

        } else if (packetId == PacketIds.Clientbound.Configuration.PING) {
            int id = byteBuf.readInt();
            sendPacket(ctx, PacketIds.Serverbound.Configuration.PONG, buffer -> buffer.writeInt(id));
            //System.out.println(username + " (" + uuid + ") ping config mode");

        }
    }


    private void channelReadPlay(ChannelHandlerContext ctx, FriendlyByteBuf byteBuf) {
        int packetId = byteBuf.readVarInt();

        if (packetId == PacketIds.Clientbound.Play.DISCONNECT) {
            System.out.println(username + " (" + uuid + ") was kicked due to " + byteBuf.readUtf());
            ctx.close();
            loginState = true;
            playState = false;

        } else if (packetId == PacketIds.Clientbound.Play.KEEP_ALIVE) {
            long id = byteBuf.readLong();
            sendPacket(ctx, PacketIds.Serverbound.Play.KEEP_ALIVE, buffer -> buffer.writeLong(id));

        } else if (packetId == PacketIds.Clientbound.Play.PING) {
            int id = byteBuf.readInt();
            sendPacket(ctx, PacketIds.Serverbound.Play.PONG, buffer -> buffer.writeInt(id));

        } else if (packetId == PacketIds.Clientbound.Play.SYNCHRONIZE_PLAYER_POSITION) {
            if (byteBuf.readableBytes() < 58) {
                return;
            }

            int id = byteBuf.readVarInt();
            double dx = byteBuf.readDouble();
            double dy = byteBuf.readDouble();
            double dz = byteBuf.readDouble();
            double velocityX = byteBuf.readDouble();
            double velocityY = byteBuf.readDouble();
            double velocityZ = byteBuf.readDouble();
            float dyaw = byteBuf.readFloat();
            float dpitch = byteBuf.readFloat();
            byte flags = byteBuf.readByte();

            x = (flags & 0x01) == 0x01 ? x + dx : dx;
            y = (flags & 0x02) == 0x02 ? y + dy : dy;
            z = (flags & 0x04) == 0x04 ? z + dz : dz;

            if (LOGS) {
                System.out.println(username + " is at " + x + "," + y + "," + z);
            }

            sendPacket(ctx, PacketIds.Serverbound.Play.CONFIRM_TELEPORTATION, buffer -> buffer.writeVarInt(id));

            isSpawned = true;

        } else if (packetId == PacketIds.Clientbound.Play.RESOURCE_PACK) {
            if (byteBuf.readableBytes() < 17) {
                return;
            }

            UUID uuid = byteBuf.readUUID();
            String url = byteBuf.readUtf();
            String hash = byteBuf.readUtf();
            boolean forced = byteBuf.readBoolean();
            String message = null;
            if (byteBuf.readBoolean()) message = byteBuf.readUtf();
            System.out.println("Resource pack info:\n" + url + "\n" + hash + "\n" + forced + "\n" + message);

            sendPacket(ctx, PacketIds.Serverbound.Play.RESOURCE_PACK, buffer -> {
                buffer.writeUUID(uuid);
                buffer.writeVarInt(RESOURCE_PACK_RESPONSE);
            });

        } else if (packetId == PacketIds.Clientbound.Play.SET_HEALTH) {
            if (byteBuf.readableBytes() < 4) {
                return;
            }

            float health = byteBuf.readFloat();

            if (health <= 0) {
                sendPacket(ctx, PacketIds.Serverbound.Play.CLIENT_COMMAND, buffer -> buffer.writeVarInt(0)); // RESPAWN
            }
        }
    }


    public void close() {
        channel.close();
    }

    public String getUsername() {
        return username;
    }

    public boolean sendTeleportCommand(double targetX, double targetZ) {
        if (channel == null || !channel.isActive() || !playState) {
            return false;
        }

        String command = "tp " + username + " " + formatCoordinate(targetX) + " ~ " + formatCoordinate(targetZ);
        sendPacket(PacketIds.Serverbound.Play.CHAT_COMMAND, buffer -> buffer.writeUtf(command));
        return true;
    }

    private void scheduleSpreadIfNeeded() {
        if (scheduledSpread) {
            return;
        }

        scheduledSpread = true;
        MinecraftStressTest.scheduleSpreadForBot(this);
    }

    private boolean isConnectionReset(Throwable cause) {
        return cause.getMessage() != null && cause.getMessage().contains("Connection reset");
    }

    private String formatCoordinate(double coordinate) {
        if (coordinate == (long) coordinate) {
            return Long.toString((long) coordinate);
        }

        return Double.toString(coordinate);
    }

    public void sendPacket(ChannelHandlerContext ctx, int packetId, Consumer<FriendlyByteBuf> applyToBuffer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(ctx.alloc().buffer());
        buffer.writeVarInt(packetId);
        applyToBuffer.accept(buffer);
        ctx.writeAndFlush(buffer);
    }

    private void sendPacket(int packetId, Consumer<FriendlyByteBuf> applyToBuffer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(channel.alloc().buffer());
        buffer.writeVarInt(packetId);
        applyToBuffer.accept(buffer);
        channel.writeAndFlush(buffer);
    }
}
