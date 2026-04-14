package com.deewend.cheshka.server.packet;

import com.deewend.cheshka.server.Packet;
import com.deewend.cheshka.server.annotation.Clientbound;

@Clientbound
public class OpponentNotFound extends Packet {
    @Override
    public int getId() {
        return 0x07;
    }
}
