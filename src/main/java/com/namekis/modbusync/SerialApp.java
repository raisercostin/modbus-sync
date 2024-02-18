package com.namekis.modbusync;

import com.fazecast.jSerialComm.SerialPort;
import com.google.common.base.Preconditions;
import io.vavr.API;
import io.vavr.collection.Seq;

public class SerialApp {
  public static void main(String[] args) {
    Seq<SerialPort> comPorts = API.Seq(SerialPort.getCommPorts());
    System.out.println(comPorts.map(x -> toString(x)).mkString("\n"));
    SerialPort comPort = comPorts.find(x -> x.getPortDescription().equals("FT232R USB UART")).get();
    boolean port = comPort.openPort();
    Preconditions.checkArgument(port, "Port not opened. Found: ", comPorts.mkString("\n"));
    try {
      while (true) {
        while (comPort.bytesAvailable() == 0) {
          Thread.sleep(20);
        }

        byte[] readBuffer = new byte[comPort.bytesAvailable()];
        int numRead = comPort.readBytes(readBuffer, readBuffer.length);
        System.out.println("Read " + numRead + " bytes.");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    comPort.closePort();
  }

  private static String toString(SerialPort x) {
    return x.getDescriptivePortName() + " " + x.getBaudRate() + " " + x.getPortLocation() + " "
        + x.getPortDescription() + " -> " + x;
  }
}
