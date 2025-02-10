package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Helper;
import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;

import java.util.Date;

@Clientbound
public class ServerHello extends Packet {
    public static final String SERVER_MOTD =
            Helper.readFully("motd.txt", "Welcome to a Cheshka server!")
                    .replace("%time%", (new Date()).toString());

    public final int magic = Helper.SERVER_HELLO_MAGIC;
    public final int serverVersionCode = Helper.SERVER_VERSION_CODE;
    public final String serverMOTD = SERVER_MOTD;

    @Override
    public int getId() {
        return 0x00;
    }
}
