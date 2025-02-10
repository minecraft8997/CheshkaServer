package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;

@Clientbound
public class HomeData extends Packet {
    public int onlinePlayerCount;
    public int activeGamesCount;

    @Override
    public int getId() {
        return 0x02;
    }
}
