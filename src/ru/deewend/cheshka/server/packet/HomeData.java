package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.Order;

@Clientbound
public class HomeData extends Packet {
    @Order(no = 1) public int onlinePlayerCount;
    @Order(no = 2) public int activeGamesCount;

    @Override
    public int getId() {
        return 0x02;
    }
}
