package com.deewend.cheshka.server;

import java.awt.image.BufferedImage;

/*
 * Strictly a 480x320 image containing only black and white pixels.
 * Each byte encodes 8 pixels in a row.
 * Each image size is always 480*320/8=19200 bytes.
 */
public class SimpleBitmapEncoder {
    public static final int WIDTH = 480;
    public static final int HEIGHT = 320;
    public static final int BYTES_IN_LINE = WIDTH / 8;
    public static final int SIZE = HEIGHT * BYTES_IN_LINE;

    public static byte[] encode(BufferedImage image) {
        if (image.getWidth() != WIDTH || image.getHeight() != HEIGHT) return null;

        byte[] result = new byte[SIZE];
        for (int i = 0; i < HEIGHT; i++) {
            int colors = 0;
            for (int j = 0; j < WIDTH; j++) {
                if (j != 0 && j % 8 == 0) {
                    result[i * BYTES_IN_LINE + (j / 8) - 1] = (byte) colors;
                    colors = 0;
                }
                int color = image.getRGB(j, i);
                // thanks https://stackoverflow.com/a/16146888/10945188
                int r = (color & 0x00FF0000) >> 16;
                int g = (color & 0x0000FF00) >> 8;
                int b = color & 0x000000FF;
                boolean black = (r + g + b) / 3 < 128;

                colors = addBit(colors, j % 8, black ? 0 : 1);
            }
        }

        return result;
    }

    private static int addBit(int n, int i, int v) {
        return n | (v << (7 - i));
    }
}
