package ru.deewend.cheshka.server.packet;

import java.util.UUID;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Serverbound;

@Serverbound
public class ClientIdentification extends Packet {
    public String username;
    public String captcha;
    public UUID clientId;

    @Override
    public int getId() {
        return 0x01;
    }
}
