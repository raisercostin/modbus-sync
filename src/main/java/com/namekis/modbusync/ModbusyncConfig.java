package com.namekis.modbusync;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import ch.qos.logback.classic.Level;
import com.namekis.modbusync.ModbusParam.ModbusType;
import com.namekis.modbusync.impl.RichEnum;
import io.vavr.collection.Iterator;
import io.vavr.collection.Map;
import org.raisercostin.jedio.Location;
import org.raisercostin.jedio.Locations;
import org.raisercostin.jedio.path.PathLocation;
import picocli.AutoComplete.GenerateCompletion;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

@Command(name = "modbusync", mixinStandardHelpOptions = true, version = "modbusync 0.1",
    description = "Synchornize backup/restore MODBUS devices.", subcommands = GenerateCompletion.class)
public class ModbusyncConfig implements Callable<Integer> {

  public static ModbusyncConfig tcp(String host, int port, int unitId) {
    return new ModbusyncConfig(new Transport(new Transport.TcpUdp(host, port), null), unitId);
  }

  public static class Transport {

    public static class TcpUdp {
      @Option(order = 0, required = true, names = "-tcp", description = "Host name/IP for MODBUS/TCP.")
      public String host;

      // MODBUS/TCP, UDP and RTU over TCP options
      @Option(order = 2, names = "-p", description = "IP protocol port number. Default: ${DEFAULT-VALUE}.",
          defaultValue = "502")
      public int port = 502;

      public TcpUdp() {
      }

      public TcpUdp(String host, int port) {
        this.host = host;
        this.port = port;
      }
    }

    public static class Serial {
      @Option(order = 0, required = true, names = "-serial", description = "Serial port when using Modbus ASCII/RTU")
      public String serialPort;

      // Modbus ASCII and RTU options
      @Option(names = { "-b", "-baudrate" }, description = "Baudrate. Default: ${DEFAULT-VALUE}.",
          defaultValue = "19200")
      public int baudrate = 19200;

      @Option(names = { "-d", "-databits" },
          description = "Databits (7 or 8 for ASCII, 8 for RTU). Default: ${DEFAULT-VALUE}.", defaultValue = "8")
      public int databits = 8;

      @Option(names = { "-s", "-stopbits" }, description = "Stopbits (1 or 2). Default: ${DEFAULT-VALUE}.",
          defaultValue = "1")
      public int stopbits = 1;

      @Option(names = "-parity", description = "Parity (none, even, odd). Default: ${DEFAULT-VALUE}.",
          defaultValue = "none")
      public String parity = "none";

      public Serial(String serialPort, int baudrate, int databits, int stopbits, String parity) {
        this.serialPort = serialPort;
        this.baudrate = baudrate;
        this.databits = databits;
        this.stopbits = stopbits;
        this.parity = parity;
      }
    }

    @ArgGroup(heading = "\nTransport: Tcp\n", exclusive = false)
    public TcpUdp tcp;

    @ArgGroup(heading = "\nTransport: Serial\n", exclusive = false)
    public Serial serial;

    public Transport() {
    }

    public Transport(TcpUdp tcp, Serial serial) {
      this.tcp = tcp;
      this.serial = serial;
    }
  }

  @ArgGroup(exclusive = true, multiplicity = "1")
  public Transport transport;
  @Option(names = { "-u", "-unitid" }, description = "Unit id or slave id", showDefaultValue = Visibility.ALWAYS)
  public int unitId = 1;
  public List<ModbusParam> params;

  public static Map<String, ModbusType> types = RichEnum.cacheByIds(ModbusType.class, x -> x.toString());

  public static class ModbusReadConverter implements CommandLine.ITypeConverter<ModbusRead> {
    @Override
    public ModbusRead convert(String value) throws Exception {
      String[] parts = value.split(",");
      if (parts.length != 3 && parts.length != 4) {
        throw new TypeConversionException(
          "ModbusRead [%s] must be in the format Type,Start,Count[,MaxBatch]".formatted(value));
      }
      ModbusType type = types.get(parts[0])
        .getOrElseThrow(() -> new TypeConversionException(
          "Type [%s] is not one of %s.".formatted(parts[0], Iterator.ofAll(types.keySet()).mkString(","))));
      int start = Integer.parseInt(parts[1]);
      int count = Integer.parseInt(parts[2]);
      Integer max = parts.length == 4 ? Integer.parseInt(parts[3]) : null;
      return new ModbusRead(type, start, count, max);
    }
  }

  public record ModbusRead(ModbusType type, int start, int count, Integer max) {
    public ModbusRead(ModbusType type, int start, int count) {
      this(type, start, count, null);
    }
  }

  @Option(names = { "-r", "--read" },
      required = true,
      description = """
          Read operations in the format Type,Start,Count,MaxBatch. Example: COIL,0,10[,130] .
            Type - coil,discrete,holding,input
              - coil - read-write binary    - F1, F5, F15
              - discrete - read binary      - F2
              - holding - read write 2bytes - F3, F6, F16
              - input - read 2bytes         - F4
            Start - start address
            Count - number of params to read
            MaxBatch - max number of addresses read in one batch. The default is the around 127bytes.
          """,
      converter = ModbusReadConverter.class)
  public ModbusRead[] reads;
  @Option(names = { "-o", "--output" }, description = "File to write csv", converter = LocationConverter.class)
  public PathLocation path;
  @Option(names = { "-f", "--force" }, description = "Overwrite output file if already exists",
      showDefaultValue = Visibility.ALWAYS)
  public boolean force = false;
  @Option(names = { "-c", "--config" }, description = "Parameters details. Manually change an output file to add them",
      converter = LocationConverter.class)
  public PathLocation config;

  public static class LocationConverter implements CommandLine.ITypeConverter<Location> {
    @Override
    public Location convert(String value) throws Exception {
      return Locations.location(value);
    }
  }

  public static class Others {
    public enum Verbosity {
      NONE(Level.OFF),
      ERROR(Level.ERROR),
      WARN(Level.WARN),
      INFO(Level.INFO),
      DEBUG(Level.DEBUG),
      TRACE(Level.TRACE);

      final Level logbackLevel;

      Verbosity(Level logbackLevel) {
        this.logbackLevel = logbackLevel;
      }
    }

    @Option(names = { "-v", "--verbosity" }, description = "Set the verbosity level: ${COMPLETION-CANDIDATES}.",
        showDefaultValue = Visibility.ALWAYS)
    public Verbosity verbosity = Verbosity.INFO;
    @Option(names = { "--debug" }, description = "Show stack trace")
    public boolean debug = false;

    public Others() {
    }
  }

  @ArgGroup(heading = "\nOthers:\n", exclusive = false)
  public Others others;

  //
  //  @Option(names = "-i", description = "Slave operates on big-endian 32-bit integers. Default: ${DEFAULT-VALUE}.")
  //  private boolean bigEndianIntegers = false;
  //
  //  @Option(names = "-f", description = "Slave operates on big-endian 32-bit floats. Default: ${DEFAULT-VALUE}.")
  //  private boolean bigEndianFloats = false;
  //
  //  @Option(names = "-1", description = "Poll only once. Default: ${DEFAULT-VALUE}.")
  //  private boolean pollOnce = false;
  //
  //  @Option(names = "-l", description = "Poll rate in ms. Default: ${DEFAULT-VALUE}.")
  //  private long pollRate = 1000;
  //
  //  @Option(names = "-o", description = "Time-out in seconds (0.01 - 10.0). Default: ${DEFAULT-VALUE}.")
  //  private double timeout = 1.0;
  public ModbusyncConfig() {
    this(null, 1);
  }

  public ModbusyncConfig(Transport transport, int unitId) {
    this.transport = transport;
    this.unitId = unitId;
    this.params = new ArrayList<>();
  }

  public ModbusyncConfig withParam(ModbusParam param) {
    this.params.add(param);
    return this;
  }

  public ModbusyncConfig withReads(ModbusRead... reads) {
    this.reads = reads;
    return this;
  }

  public ModbusyncConfig withParams(List<ModbusParam> params) {
    this.params = params;
    return this;
  }

  @Override
  public Integer call() throws Exception {
    try (ModbusyncApp app = new ModbusyncApp(this)) {
      app.execute();
    }
    return 0;
  }

  public ModbusyncConfig withPath(PathLocation path) {
    this.path = path;
    return this;
  }
}
