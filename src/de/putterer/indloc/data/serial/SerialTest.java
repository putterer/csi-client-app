package de.putterer.indloc.data.serial;

import com.fazecast.jSerialComm.SerialPort;
import lombok.val;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

public class SerialTest {

	public static void main(String args[]) throws IOException {
		val serialPort = SerialPort.getCommPort("/dev/ttyUSB0");

		serialPort.setBaudRate(115200);
		serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
		serialPort.openPort();

		Scanner reader = new Scanner(new InputStreamReader(serialPort.getInputStream()));
		byte data[] = new byte[1024];
		SerialClient.serialReadLine(serialPort);
		while(serialPort.isOpen()) {
//			int bytesRead = serialPort.readBytes(data, 1024);
//			System.out.println(new String(data, 0, bytesRead));
			try {
				String s = SerialClient.serialReadLine(serialPort);
				System.out.println(Integer.parseInt(s));
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
//			System.out.println(reader.nextLine()); // TODO is this realtime? could use serialPort.read() instead
//			System.out.println(new String());
		}
	}

}
