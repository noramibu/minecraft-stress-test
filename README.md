# minecraft-stress-test

Automate the stress testing of your 1.21 Minecraft server with bots.
This project will log offline-mode bots into the specified server which will
stay connected until you run console commands.

## Building

Download the source code with

```shell
git clone https://github.com/PureGero/minecraft-stress-test.git
```

Build the source code with

```shell
mvn
```

## Running

Ensure the following values are set in your server.properties:

```properties
online-mode=false
allow-flight=true
```

Run the bot with

```shell
java -jar target/minecraft-stress-test-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

Or, specify optional parameters:

```shell
java
    -Dbot.count=1
    -Dbot.name=TreasBot
    -Dbot.ip=127.0.0.1
    -Dbot.port=25565
    -Dbot.login.delay.ms=100
    -Dbot.join.teleport.delay.ms=10000
    -Dbot.reconnect.delay.ms=30000
    -Dbot.autocount.interval.ms=60000
    -Dbot.spread.distance=256
    -Dbot.spread.command.delay.ms=100
    -Dbot.team.size=10
    -Dbot.team.gap=25
    -jar target/minecraft-stress-test-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

Note that bots are unable to respawn, we recommend giving them creative mode.

### Commmands

You can type some commands into the console to control the bots on the fly.

`count 1000`

Set the bot count to 1000. This will either connect more bots, or disconnect existing bots as needed.

`autocount 100 60s`

Increase the target bot count by 100 every 60 seconds. New bots use the same active spread or team
configuration after they join. The time supports `ms`, `s`, `m`, and `h` suffixes. Use
`autocount stop` to stop automatic increases.

`spread 256`

Make each bot run a `/tp <bot name> <x> ~ <z>` command so bots are placed in a grid. Every
`spread <distance>` command becomes the active outer spread configuration; bots added later with
`count` use the latest spread distance after the join teleport delay. If team mode is active,
`spread` changes the distance between team centers and keeps team members together.

`team 10 25`

Put bots into teams of 10, with 25 blocks between members inside the same team. Team centers use
the current spread distance, so with 100 bots this creates 10 teams.

`spreaddelay 250`

Set the delay between bot `/tp` commands during spread. Increase this if the server disconnects
bots while many teleport commands are being sent.

`reconnectdelay 30000`

Set how long disconnected bots wait before reconnecting. Bots removed by lowering `count` will not
reconnect.

`joindelay 3000`

Set how long newly joined or reconnected bots wait before teleporting into their active spread or
team slot. This delay is not multiplied by bot index.
