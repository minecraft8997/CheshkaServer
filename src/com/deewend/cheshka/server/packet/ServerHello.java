package com.deewend.cheshka.server.packet;

import com.deewend.cheshka.server.Helper;
import com.deewend.cheshka.server.Packet;
import com.deewend.cheshka.server.annotation.Clientbound;
import com.deewend.cheshka.server.annotation.Order;

import java.util.Date;

@Clientbound
public class ServerHello extends Packet {
    public static final String SERVER_MOTD =
            Helper.readFully("motd.txt", "Welcome to a Cheshka server!")
                    .replace("%time%", (new Date()).toString());

    @Order(no = 1) public final int magic = Helper.SERVER_HELLO_MAGIC;
    @Order(no = 2) public final int serverVersionCode = Helper.SERVER_VERSION_CODE;
    @Order(no = 3) public final String serverMOTD = SERVER_MOTD;

    @Override
    public int getId() {
        return 0x00;
    }
}
