package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.OnlyWhen;
import ru.deewend.cheshka.server.annotation.Order;

import java.awt.image.BufferedImage;

@Clientbound
public class HomeData extends Packet {
    @Order(no = 1) public boolean hasServerLogo;
    @OnlyWhen(field = "hasServerLogo", is = "true")
    @Order(no = 2) public BufferedImage serverLogo;
    @Order(no = 3) public int onlinePlayerCount;
    @Order(no = 4) public int activeGamesCount;

    @Override
    public int getId() {
        return 0x02;
    }
}
