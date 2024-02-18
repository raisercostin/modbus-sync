package com.namekis.modbusync;

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "modpoll", mixinStandardHelpOptions = true, version = "modpoll 1.0",
    description = "Communicates with MODBUS slave devices.")
public class ModPollSubcommand implements Callable<Integer> {
  public static void main(String[] args) throws Exception {
    ModPollSubcommand modPollOptions = new ModPollSubcommand();
    int exitCode = new CommandLine(modPollOptions).execute("192.168.1.112".split("\s+"));
    System.exit(exitCode);
    //
    //    // ModPoll.main(new String[] {});
    //    // ModPoll.main("-a 1 -m2 tcp -p 8899 -t 1 -r 1 -c 125 -1 192.168.1.112".split("\s+"));
    //    read("--tcp 192.168.1.112 --tcp-port 8899 --config chofu3.csv".split("\s+"));
    //    List<String[]> config = readAllLines();
  }

  enum Protocol {
    ascii,
    rtu,
    tcp,
    udp,
    enc
  }

  enum DataType {
    discrete_input("1"),
    input_register("3"),
    input_register_hex("3:hex"),
    input_register_int("3:int"),
    input_register_mod("3:mod"),
    input_register_float("3:float"),
    holding_register("4"),
    holding_register_hex("4:hex"),
    holding_register_int("4:int"),
    holding_register_mod("4:mod"),
    holding_register_float("4:float");

    private final String code;

    DataType(String code) {
      this.code = code;
    }

    @Override
    public String toString() {
      return code;
    }
  }

  @Parameters(index = "0", description = "Serial port when using Modbus ASCII/RTU or host name/IP for MODBUS/TCP.")
  public String serialPortOrHost;

  public static class ProtocolDetails {

    public static class Tcp {
      @Option(names = "-m=tcp", description = "Modbus protocol: ${COMPLETION-CANDIDATES}.")
      private boolean protocol;

      // MODBUS/TCP, UDP and RTU over TCP options
      @Option(names = "-p", description = "IP protocol port number. Default: ${DEFAULT-VALUE}.")
      public int port = 502;
    }

    @ArgGroup(exclusive = false)
    public Tcp tcp;

    public static class Serial {
      // Modbus ASCII and RTU options
      @Option(names = "-b", description = "Baudrate. Default: ${DEFAULT-VALUE}.")
      private int baudrate = 19200;

      @Option(names = "-d", description = "Databits (7 or 8 for ASCII, 8 for RTU). Default: ${DEFAULT-VALUE}.")
      private int databits = 8;

      @Option(names = "-s", description = "Stopbits (1 or 2). Default: ${DEFAULT-VALUE}.")
      private int stopbits = 1;

      @Option(names = "-parity", description = "Parity (none, even, odd). Default: ${DEFAULT-VALUE}.")
      private String parity = "even";
    }

    @ArgGroup(exclusive = false)
    public Serial serial;
  }

  @ArgGroup(exclusive = true)
  ProtocolDetails protocolDetails;

  @Option(names = "-m", description = "Modbus protocol: ${COMPLETION-CANDIDATES}.")
  private Protocol protocol;

  @Option(names = "-a", description = "Slave address (1-255 for serial, 0-255 for TCP). Default: ${DEFAULT-VALUE}.")
  private int slaveAddress = 1;

  @Option(names = "-r", description = "Start reference (1-65536). Default: ${DEFAULT-VALUE}.")
  private int startReference = 1;

  @Option(names = "-c", description = "Number of values to poll (1-125). Default: ${DEFAULT-VALUE}.")
  private int numberOfValues = 1;

  @Option(names = "-t", description = "Data type. Default: ${DEFAULT-VALUE}. Values: ${COMPLETION-CANDIDATES}")
  private DataType dataType = DataType.holding_register;

  @Option(names = "-i", description = "Slave operates on big-endian 32-bit integers. Default: ${DEFAULT-VALUE}.")
  private boolean bigEndianIntegers = false;

  @Option(names = "-f", description = "Slave operates on big-endian 32-bit floats. Default: ${DEFAULT-VALUE}.")
  private boolean bigEndianFloats = false;

  @Option(names = "-1", description = "Poll only once. Default: ${DEFAULT-VALUE}.")
  private boolean pollOnce = false;

  @Option(names = "-l", description = "Poll rate in ms. Default: ${DEFAULT-VALUE}.")
  private long pollRate = 1000;

  @Option(names = "-o", description = "Time-out in seconds (0.01 - 10.0). Default: ${DEFAULT-VALUE}.")
  private double timeout = 1.0;

  @Override
  public Integer call() throws Exception {
    throw new RuntimeException("Not implemented yet!!!");
  }
}
