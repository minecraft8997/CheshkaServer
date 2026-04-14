package com.deewend.cheshka.server.packet;

import com.deewend.cheshka.server.Packet;
import com.deewend.cheshka.server.annotation.Clientbound;
import com.deewend.cheshka.server.annotation.Serverbound;

@Clientbound
@Serverbound
public class Resign extends Packet {
    @Override
    public int getId() {
        return 0x09;
    }
}
