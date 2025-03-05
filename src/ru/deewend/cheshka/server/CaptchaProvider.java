package ru.deewend.cheshka.server;

import nl.captcha.Captcha;
import nl.captcha.backgrounds.FlatColorBackgroundProducer;
import nl.captcha.text.renderer.DefaultWordRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class CaptchaProvider {
    private CaptchaProvider() {
    }

    public static Pair<BufferedImage, String> generateCaptcha() {
        Captcha.Builder captchaBuilder = new Captcha.Builder(480, 320)
                .addText(new DefaultWordRenderer(
                        List.of(Color.BLACK),
                        List.of(
                                new Font("Arial", Font.BOLD, 144),
                                new Font("Courier", Font.BOLD, 144)
                        )
                ))
                .addBackground(new FlatColorBackgroundProducer(Color.WHITE));

        int noiseIterations = (Math.random() < 0.5D ? 16 : 32);
        for (int i = 0; i < noiseIterations; i++) captchaBuilder = captchaBuilder.addNoise();
        Captcha captcha = captchaBuilder.build();

        return new Pair<>(captcha.getImage(), captcha.getAnswer());
    }
}
