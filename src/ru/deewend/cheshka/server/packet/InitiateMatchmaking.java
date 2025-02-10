package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.OnlyWhen;
import ru.deewend.cheshka.server.annotation.Serverbound;

@Serverbound
public class InitiateMatchmaking extends Packet {
    public byte mode;
    @OnlyWhen(field = "mode", is = "0")
    public String invitationCode;

    @Override
    public int getId() {
        return 0x02;
    }
}
