package com.kircherelectronics.main;

import java.io.IOException;
import java.util.Random;

import jd2xx.JD2XX;

/*  This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Logs RPM from an Audi ME7 M-box ECU. Uses a wrapper of ftd2xx.dll to perform
 * the serial communication. Should work with any FTDI based VAG cable (needs
 * the K-line) including the Ross-Tech cables...
 * 
 * Note... You should not use this if you don't understand what you are doing.
 * It is a demonstration of concept, don't plug it in and expect it to actually
 * log anything (it will probably blow up your car, your house and your dog).
 * 
 * @author Kaleb
 * 
 */
public class KWP2000
{
	private JD2XX serialPort;

	public KWP2000()
	{
		System.out.println("KWP2000");

		try
		{
			// Slow-init "5 baud"
			// To initialize to KWP2000 use the 0x11 address...
			// To initialize to ISO 9141 use the 0x33 address...
			if (init(0x11))
			{
				System.out.println("KWP2000 Init complete.");

				// Start a diagnostic session...
				startDiagnosticSession();

				// Expect a 10 byte response from start diagnostic session...
				byte[] packet = serialPort.read(10);

				// Print the response...
				for (int i = 0; i < packet.length; i++)
				{
					System.out.print(" 0x"
							+ Integer.toHexString(0xff & packet[i]));
				}

				System.out.println("");

				// Write our ddli table
				writeDdliTable();

				// Expect a 21 byte response...
				packet = serialPort.read(21);

				// Print the resonse...
				for (int i = 0; i < packet.length; i++)
				{
					System.out.print(" 0x"
							+ Integer.toHexString(0xff & packet[i]));
				}

				System.out.println("");

				// Write our data table...
				writeDataTable();

				// Expect a 111 byte response...
				packet = serialPort.read(111);

				// Print our response...
				for (int i = 0; i < packet.length; i++)
				{
					System.out.print(" 0x"
							+ Integer.toHexString(0xff & packet[i]));
				}

				System.out.println("");

				// Start logging RPM
				while (true)
				{
					// read data by local identifier
					// byte 0 = 0x21 -> readDataByLocalIdentifier
					// byte 1 = f0 -> recordLocalIdentifier
					byte[] bytes = new byte[]
					{ (byte) 0x21, (byte) 0xf0 };

					writePacket(bytes);

					// We expect to read 10 bytes
					// byte 0-3 = 0x02 0x21 0xf0 0x13 -> the request message
					// echo
					// byte 4 = the response length
					// byte 5 = 0x61 -> positive response
					// byte 6 = 0xf0 -> recordLocalIdentifer
					// byte 7-8 = two byte response for RPM
					// byte 9 = checksum
					packet = serialPort.read(11);

					// Bytes are swapped...
					int a = 0xff & packet[9];
					int b = 0xff & packet[8];

					// Standard conversion for two byte RPM
					System.out.println(((a * 256) + b) / 4);
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * The address to initialize to... 0x11 = KWP2000; 0x33 = ISO 9141
	 * 
	 * @param address
	 *            the address to be initialized
	 * @return success
	 * @throws Exception
	 */
	private boolean init(int address) throws Exception
	{
		serialPort = new JD2XX();

		Object[] devs;
		try
		{
			// Find our device...
			devs = serialPort.listDevicesByLocation();

			// If nothing exists...
			if (devs.length == 0)
			{
				System.out.println("No devices found...");

				return false;
			}

			// List our devices...
			for (int i = 0; i < devs.length; ++i)
			{
				System.out.println("Device: " + devs[i]);
			}

			// Only if we have a device...
			if (devs.length > 0)
			{
				// We just assume the first index
				serialPort.open(0);

				System.out.println("Slow init...");

				// Slow init (5 baud)...
				slowInit(address);

				// Now we set up our serial port to listen for the resonse...
				serialPort.setBaudRate(10400);
				serialPort.setDataCharacteristics(8, JD2XX.STOP_BITS_1,
						JD2XX.PARITY_NONE);

				int hex = serialPort.read();

				while (hex != 0x8F)
				{
					// Wait for 0x8F...
					hex = serialPort.read();
					System.out.println("Want 0x8F... Received " + "0x"
							+ Integer.toHexString(hex) + " instead...");
				}

				System.out.println("0x8F Recieved");

				Thread.sleep(25);

				serialPort.write(0x70);

				hex = serialPort.read();

				while (hex != 0xFF - address)
				{
					// Wait for 0xFF...
					hex = serialPort.read();
					System.out.println("Want "
							+ Integer.toHexString(0xFF - address)
							+ "... Received " + "0x" + Integer.toHexString(hex)
							+ " instead...");
				}

				System.out.println(Integer.toHexString(0xFF - address)
						+ " Recieved");

				return true;
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Slow init (5 baud)... The FTDI driver doesn't do 5 baud, so we actually
	 * pull the line high and low at 5 baud and bang out the address in bits.
	 * 
	 * @param address
	 *            initialize to this address...
	 * @throws Exception
	 */
	private void slowInit(int address) throws Exception
	{
		// The K-Line must be high for at least 300ms
		serialPort.setBreakOff();
		Thread.sleep(300);

		// Start bit, 200ms low
		serialPort.setBreakOn();
		Thread.sleep(200);

		// Bang the bits (one byte) of our address
		for (int i = 0; i < 8; i++)
		{
			if (((0x01 << i) & address) > 0)
			{
				serialPort.setBreakOff();
			}
			else
			{
				serialPort.setBreakOn();
			}

			Thread.sleep(200);
		}

		serialPort.setBreakOff();
	}

	/**
	 * Write the packet...
	 * 
	 * @param packet
	 *            the packet to be written.
	 */
	private void writePacket(byte[] packet)
	{
		packet = insertPacketChecksum(insertPacketLength(packet));

		try
		{
			serialPort.write(packet);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * The data table is pointed-to by the ddli table and keeps track of the
	 * memory addresses of the parameters that will be logged.
	 */
	private void writeDataTable()
	{
		// *NOTE* This defines a data for an m-box ecu intended to read 1
		// parameter from a data table (in this case RPM).

		// Unused ram space to store our table (m-box)
		// byte 0 = 0x3d -> write memory by address
		// byte 1/3 = 0x38 0x6d 0xa4 -> memory address
		byte[] address = new byte[]
		{ (byte) 0x3d, (byte) 0x38, (byte) 0x6d, (byte) 0xa4 };

		// 97 bytes, plus the memory byte, plus the address = 102 bytes total
		byte memorySize = (byte) 0x61;

		// One parameter where each parameter is 6 bytes
		byte[][] parameters = new byte[1][6];

		// The single parameter (RPM)
		// RPM Address (M box) 0x0000F878
		// byte 0 = 0x02 -> two bytes response
		// byte 1 = 0x41 unused
		// byte 2-5 = RPM address
		parameters[0] = new byte[]
		{ (byte) 0x02, (byte) 0x41, (byte) 0x78, (byte) 0xf8, (byte) 0x00,
				(byte) 0x00 };

		// Our final packet will be 102 bytes (before the length byte or
		// checksum)
		byte[] packet = new byte[102];

		for (int i = 0; i < address.length; i++)
		{
			packet[i] = address[i];
		}

		// Keep track of what index we are working with
		int offset = address.length;

		// Add the number memory size
		packet[offset++] = memorySize;

		// Fill the packet with our parameters
		for (int i = 0; i < parameters.length; i++)
		{
			for (int j = 0; j < parameters[i].length; j++)
			{
				packet[offset++] = parameters[i][j];
			}
		}

		// We fill up the remaining bytes in the packet with pseudo-random bytes
		Random r = new Random();
		int toFill = packet.length - offset;
		for (int i = 0; i < toFill; i++)
		{
			// pseudo-random fill
			packet[offset++] = (byte) (176 + r.nextInt(10));
		}

		writePacket(packet);
	}

	/**
	 * Write a DynamicallyDefinedLocalIdentifer table. This writes a ddli table
	 * to unused RAM that points to our data table (the data table keeps track
	 * of the memory addresses of the parameters we want to log). We can then
	 * use readDataByLocalIdentifer to read the entire data table with one call.
	 */
	private void writeDdliTable()
	{
		// *NOTE* This defines a ddli for an m-box ecu intended to read 1
		// parameter from a data table (in this case RPM).

		// Unused ram space to store our table (m-box)
		// byte 0 = 0x3d -> write memory by address
		// byte 1/3 = 0x38 0x07 0x92 -> memory address
		byte[] address = new byte[]
		{ (byte) 0x3d, (byte) 0x38, (byte) 0x07, (byte) 0x92 };

		// 8 bytes, plus the memory byte, plus the address = 31 bytes total
		byte memorySize = (byte) 0x08;

		// Create our ddli table
		// byte 0-1 = 0x41 0x52 -> valid flag (normally 0x00 0x01 to indicate a
		// used entry)
		// byte 2 = 0x01 -> number of entries in the data table
		// byte 3 = 0x00 -> unused
		// byte 4-7 = 0xa4 0x6d 0x38 0x00 -> pointer to our data table
		byte[] ddli = new byte[]
		{ (byte) 0x41, (byte) 0x52, (byte) 0x01, (byte) 0x00, (byte) 0xa4,
				(byte) 0x6d, (byte) 0x38, (byte) 0x00 };

		// Our final packet will be 13 bytes (before the length byte or
		// checksum)
		byte[] packet = new byte[13];

		for (int i = 0; i < address.length; i++)
		{
			packet[i] = address[i];
		}

		// Keep track of what index we are working with
		int offset = address.length;

		// Add the number memory size
		packet[offset++] = memorySize;

		// Fill the packet with our ddli
		for (int i = 0; i < ddli.length; i++)
		{
			packet[offset++] = ddli[i];
		}

		for (int i = 0; i < packet.length; i++)
		{
			System.out.print(" 0x" + Integer.toHexString(0xff & packet[i]));
		}

		System.out.println("");

		writePacket(packet);
	}

	/**
	 * Start a diagnostic session and a development session (unsure if a
	 * standard session would work, but it might)
	 */
	private void startDiagnosticSession()
	{
		// byte 0 = 0x10 -> start diagnostic session
		// byte 1 = 0x86 -> start development session
		// byte 2 = 0x14 -> baud 10400
		byte[] packet = new byte[]
		{ (byte) 0x10, (byte) 0x86, (byte) 0x14 };

		writePacket(packet);
	}

	/**
	 * Insert a packet checksum byte including the length byte...
	 * 
	 * @param packet
	 *            the packet with length byte included
	 * @return the packet with the checksum appended
	 */
	private byte[] insertPacketChecksum(byte[] packet)
	{
		byte checksum = 0;

		byte[] newPacket = new byte[packet.length + 1];

		for (int i = 0; i < packet.length; i++)
		{
			// Java assumes signed bytes, and we want unsigned so AND with 0xFF
			checksum += 0xff & packet[i];
		}

		for (int i = 0; i < newPacket.length; i++)
		{
			// Append the checksum
			if (i == newPacket.length - 1)
			{
				newPacket[newPacket.length - 1] = checksum;
			}
			// Otherwise just transfer the packet
			else
			{
				newPacket[i] = packet[i];
			}
		}

		return newPacket;
	}

	/**
	 * Insert the packet length byte.
	 * 
	 * @param packet
	 *            the packet to have the length byte added
	 * @return the packet with the length byte added
	 */
	private byte[] insertPacketLength(byte[] packet)
	{
		// *NOTE* There is some weirdness with sending long packets... The ECU
		// seems to want odd numbers of bytes so I enforce that. The odd number
		// length doesn't seem to effect smaller packets, but it doesn't mind if
		// they are odd number, either... so just always do it.

		byte length = (byte) packet.length;

		byte[] newPacket;

		// Force odd number packet lengths by padding with 0x00
		if (length % 2 != 0)
		{
			newPacket = new byte[packet.length + 1];

			for (int i = 0; i < newPacket.length; i++)
			{
				if (i == 0)
				{
					// Length is the first byte
					newPacket[0] = length;
				}
				else
				{
					// Fill in the rest of the packet
					newPacket[i] = packet[i - 1];
				}
			}
		}
		else
		{
			newPacket = new byte[packet.length + 2];

			// pad 0x00 to make the packet length odd
			newPacket[0] = (byte) 0x00;

			for (int i = 1; i < newPacket.length; i++)
			{
				if (i == 1)
				{
					// Length is the first byte
					newPacket[1] = length;
				}
				else
				{
					// Fill in the rest of the packet
					newPacket[i] = packet[i - 2];
				}
			}
		}

		return newPacket;
	}
}
