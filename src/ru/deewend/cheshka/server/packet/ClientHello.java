package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Order;
import ru.deewend.cheshka.server.annotation.Serverbound;

@Serverbound
public class ClientHello extends Packet {
    @Order(no = 1) public int magic;
    @Order(no = 2) public int clientVersionCode;
    @Order(no = 3) public byte protocolVersion;
    @Order(no = 4) public String serverAddress;
    @Order(no = 5) public int serverPort;
    @Order(no = 6) public String language; // ISO 639 two-letter code
    @Order(no = 7) public byte reserved;

    @Override
    public int getId() {
        return 0x00;
    }
}
