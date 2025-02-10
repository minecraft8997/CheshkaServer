package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Serverbound;

@Serverbound
public class ClientHello extends Packet {
    public int magic;
    public int clientVersionCode;
    public byte protocolVersion;
    public String serverAddress;
    public int serverPort;
    public String language; // ISO 639 two-letter code
    public byte reserved;

    @Override
    public int getId() {
        return 0x00;
    }
}
