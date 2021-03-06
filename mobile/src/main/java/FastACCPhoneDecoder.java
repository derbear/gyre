import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by derek on 5/11/15.
 */
public class FastACCPhoneDecoder {

    public static void main(String[] args) throws IOException {
        String filename = args[0];
        DataInputStream input = new DataInputStream(new FileInputStream(new File(filename)));

        while (true) {
            // 1: byte: sensor source
            byte type = input.readByte();
            if (type == 0) {
                System.out.print("accelerometer");
            } else {
                System.out.print("gyroscope");
            }
            System.out.print(',');

            // 2: integer: packet number
            int packetNum = input.readInt();
            System.out.print(packetNum);
            System.out.print(',');

            // 3: long: write timestamp
            long timestamp = input.readLong();
            System.out.print(timestamp);
            System.out.print(',');

            // 4: long: data timestamp
            timestamp = input.readLong();
            System.out.print(timestamp);
            System.out.print(',');

            // 5: float[3]: x, y, z
            float x = input.readFloat();
            float y = input.readFloat();
            float z = input.readFloat();
            System.out.print(x);
            System.out.print(',');
            System.out.print(y);
            System.out.print(',');
            System.out.print(z);
            System.out.print(',');

            System.out.println();
        }
    }
}
