import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class Util {
    static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    static void EmptyDirectory(Path path) {
        try {
            Files.walk(path).sorted(Comparator.reverseOrder()).forEach(path1 -> {
                try {

                    Files.delete(path1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void CopyDirectory(Path src, Path dst) {
        try {
            Files.walk(src).forEach(f -> {
                Path targetFile = dst.resolve(src.relativize(f));
                try {
                    if(Files.isDirectory(f))
                    {
                        if(!Files.exists(targetFile))
                            Files.createDirectory(targetFile);
                    }
                    else
                        Files.copy(f, targetFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
