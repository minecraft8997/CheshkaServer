package ru.deewend.cheshka.server;

import nl.captcha.Captcha;
import nl.captcha.backgrounds.FlatColorBackgroundProducer;
import nl.captcha.text.renderer.DefaultWordRenderer;
import ru.deewend.cheshka.server.annotation.Order;
import ru.deewend.cheshka.server.packet.HomeData;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.List;
import java.util.zip.CRC32;

public class Helper {
    public interface Providable<T> {
        void provide(T object) throws Exception;
    }

    public static final int SERVER_VERSION_CODE = 1;
    public static final String DEFAULT_STRING_VALUE = "";
    public static final String NULL_UUID = "00000000-0000-0000-0000-000000000000";
    public static final UUID NULL_UUID_OBJ = UUID.fromString(NULL_UUID);
    public static final int MAX_FIELD_SIZE = 65535;

    /*
     * 11 is an attempt to represent H;
     * 5  is an attempt to represent S;
     * K  is skipped;
     *
     * CHESH[K]A.
     */
    public static final int CLIENT_HELLO_MAGIC = 0xC11E511A;

    /*
     * Expected to be equal to 0xDEE111ED.
     *
     * 111 is an attempt to represent W;
     * N   is skipped.
     *
     * DEEWE[N]D.
     */
    public static final int SERVER_HELLO_MAGIC = 0xDEE111ED;

    public static final int SIGNATURE_MAGIC = 0xA115E11C; // reverted Helper.CLIENT_HELLO_MAGIC

    private Helper() {
    }

    public static String getProperty(String key, String defaultValue) {
        key = CheshkaServer.PROPERTY_PREFIX + key;
        String result = System.getProperty(key, defaultValue);
        if (CheshkaServer.SHOW_PROPERTIES) {
            System.out.println("-D" + key + "=\"" + result + "\"" + (result.equals(defaultValue) ? " [default]" : ""));
        }

        return result;
    }

    public static void newThread(String name, Runnable task, boolean platformThread) {
        if (platformThread) {
            Thread.ofPlatform().name(name).daemon().start(task);
        } else {
            Thread.ofVirtual().name(name).start(task); // already a daemon thread
        }
    }

    public static int calculateCRC32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);

        return (int) crc32.getValue();
    }

    private static byte[] readByteArray(DataInputStream stream) throws IOException {
        int length = stream.readUnsignedShort();
        int signature = stream.readInt();

        byte[] contents = new byte[length];
        stream.readFully(contents);

        if ((signature ^ SIGNATURE_MAGIC) != calculateCRC32(contents)) {
            throw new IOException("Bad signature");
        }

        return contents;
    }

    public static void writeByteArray(DataOutputStream stream, byte[] contents) throws IOException {
        int length = contents.length;

        if (length > MAX_FIELD_SIZE) {
            throw new RuntimeException("Array is too long");
        }
        stream.writeShort(length);
        int signature = calculateCRC32(contents) ^ SIGNATURE_MAGIC;
        stream.writeInt(signature);

        stream.write(contents);
    }

    public static String readString(DataInputStream stream) throws IOException {
        byte[] contents = readByteArray(stream);

        return new String(contents, StandardCharsets.UTF_8);
    }

    public static void writeString(DataOutputStream stream, String str) throws IOException {
        byte[] contents = str.getBytes(StandardCharsets.UTF_8);

        writeByteArray(stream, contents);
    }

    public static void writeBufferedImage(DataOutputStream stream, BufferedImage image) throws IOException {
        ByteArrayOutputStream stream0 = new ByteArrayOutputStream(MAX_FIELD_SIZE / 2);
        ImageIO.write(image, "png", stream0);

        writeByteArray(stream, stream0.toByteArray());
    }

    public static List<Field> fixOrder(Field[] fields) {
        List<Field> annotatedOnly = new ArrayList<>();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Order.class)) annotatedOnly.add(field);
        }
        annotatedOnly.sort((first, second) -> {
            int firstNo = first.getAnnotation(Order.class).no();
            int secondNo = second.getAnnotation(Order.class).no();

            return firstNo - secondNo;
        });

        return annotatedOnly;
    }

    @SuppressWarnings({"unchecked", "SynchronizationOnLocalVariableOrMethodParameter"})
    public static <T extends Packet> T findPacket(ClientHandler handler, Class<T> clazz) {
        synchronized (handler) {
            for (Packet packet : handler.gameRoomPacketQueue) {
                if (clazz.isInstance(packet)) return (T) packet;
            }
        }

        return null;
    }

    public static String getClassName(Object object) {
        Class<?> clazz;
        if (object instanceof Class) {
            clazz = (Class<?>) object;
        } else {
            clazz = object.getClass();
        }

        return clazz.getName();
    }

    public static String readFully(String filename, String defaultValue) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }

            return builder.toString();
        } catch (IOException e) {
            if (e instanceof FileNotFoundException) {
                try (OutputStream stream = Files.newOutputStream(Paths.get(filename), StandardOpenOption.CREATE)) {
                    stream.write(defaultValue.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ex) {
                    Log.w("Failed to create " + filename + " and write the default value", ex);
                }
            } else {
                Log.w("I/O issue when reading " + filename, e);
            }

            return defaultValue;
        }
    }

    public static HomeData craftHomeData(CheshkaServer server) {
        HomeData homeData = new HomeData();
        homeData.onlinePlayerCount = server.getOnlinePlayerCount();
        server.accessGameRooms(gameRooms -> homeData.activeGamesCount = gameRooms.size());

        return homeData;
    }

    public static boolean checkInvitationCode(String code) {
        if (code.length() != 4) return false;

        for (int i = 0; i < code.length(); i++) {
            char currentChar = code.charAt(i);
            if (currentChar >= 'a' && currentChar <= 'f') continue;
            if (currentChar >= '0' && currentChar <= '9') continue;

            return false;
        }

        return true;
    }

    public static boolean validateUsername(String username) {
        if (username == null) {
            return false;
        }
        if (username.length() < 2 || username.length() > 16) {
            return false;
        }
        for (int i = 0; i < username.length(); i++) {
            char current = username.charAt(i);
            if (current == '_' || current == '.') {
                continue; // this is allowed
            }
            if (current >= '0' && current <= '9') {
                continue; // numbers are allowed too
            }
            if ((current >= 'a' && current <= 'z') || (current >= 'A' && current <= 'Z')) {
                continue; // characters from the Latin alphabet are allowed, of course
            }

            return false; // unknown character
        }

        return true;
    }

    public static void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
