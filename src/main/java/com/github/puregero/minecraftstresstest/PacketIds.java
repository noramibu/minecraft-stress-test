package com.github.puregero.minecraftstresstest;

public final class PacketIds {
    private PacketIds() {
    }

    public static final class Clientbound {
        private Clientbound() {
        }

        public static final class Login {
            private Login() {
            }

            public static final int
                    DISCONNECT = 0x00,
                    ENCRYPTION_REQUEST = 0x01,
                    LOGIN_SUCCESS = 0x02,
                    SET_COMPRESSION = 0x03;
        }

        public static final class Configuration {
            private Configuration() {
            }

            public static final int
                    DISCONNECT = 0x02,
                    FINISH_CONFIGURATION = 0x03,
                    KEEP_ALIVE = 0x04,
                    PING = 0x05;
        }

        public static final class Play {
            private Play() {
            }

            //client outbound
            public static final int
                    DISCONNECT = 0x20,
                    KEEP_ALIVE = 0x2B,
                    PING = 0x3B,
                    SYNCHRONIZE_PLAYER_POSITION = 0x46,
                    RESOURCE_PACK = 0x4F,
                    SET_HEALTH = 0x66;
        }

    }

    public static final class Serverbound {
        private Serverbound() {
        }

        public static final class Handshaking {
            private Handshaking() {
            }

            public static final int
                    HANDSHAKE = 0x00;
        }

        public static final class Login {
            private Login() {
            }

            public static final int
                    LOGIN_START = 0x00,
                    LOGIN_ACKNOWLEDGED = 0x03;
        }

        public static final class Configuration {
            private Configuration() {
            }

            public static final int
                    CLIENT_INFORMATION = 0x00,
                    FINISH_CONFIGURATION = 0x03,
                    KEEP_ALIVE = 0x04,
                    PONG = 0x05,
                    KNOWN_PACKS = 0x07;
        }

        public static final class Play {
            private Play() {
            }

            public static final int
                    CONFIRM_TELEPORTATION = 0x00,
                    CLIENT_RESPAWN = 0x0B,
                    KEEP_ALIVE = 0x1B,
                    SET_PLAYER_POSITION_AND_ROTATION = 0x1E,
                    PONG = 0x2C,
                    RESOURCE_PACK = 0x30;
        }

    }

}