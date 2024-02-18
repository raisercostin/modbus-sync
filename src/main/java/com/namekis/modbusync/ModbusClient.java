package com.namekis.modbusync;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.ModbusException;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.util.BitVector;
import com.google.common.base.Preconditions;
import com.namekis.modbusync.ModbusParam.ModbusFunction;
import com.namekis.modbusync.ModbusParam.ModbusType;
import com.namekis.modbusync.ModbusyncConfig.Transport;
import com.namekis.modbusync.ModbusyncConfig.Transport.TcpUdp;
import com.opencsv.CSVReader;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.code.DataType;
import com.serotonin.modbus4j.exception.ErrorResponseException;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.locator.BaseLocator;
import com.serotonin.modbus4j.locator.NumericLocator;
import io.vavr.API;
import io.vavr.collection.Iterator;
import io.vavr.collection.Seq;
import io.vavr.control.Try;
import picocli.CommandLine;

public class ModbusClient implements AutoCloseable {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ModbusClient.class);

  interface FunctionCodeExtension {
    int code();
  }

  public final ModbusMaster modbus4j;
  public final ModbusTCPMaster j2mod;
  public final int unitId;

  public ModbusClient(Transport transport, int unitId) {
    String address = transport.tcp.host;
    int port = transport.tcp.port;
    //Presupunând utilizarea bibliotecii Modbus4J
    //    ModbusFactory factory = new ModbusFactory();
    //    SerialPortWrapper params = new SerialPortWrapper();
    //    params.setCommPortId("/dev/ttyUSB1");
    //    params.setBaudRate(9600);
    //    params.setDataBits(8);
    //    params.setStopBits(1);
    //    params.setParity(0);
    //    IpParameters params = new IpParameters();
    //    params.setHost(address);
    //    params.setPort(port);
    //    //params.setLingerTime(501);
    //    params.setEncapsulated(false);
    this.unitId = unitId;
    this.modbus4j = null;
    //    this.modbus4j = factory.createTcpMaster(params, true, -1);
    //    modbus4j.setExceptionHandler(e -> {
    //      if (e instanceof WaitingRoomException) {
    //        log.trace("modbus implementation error?", e);
    //      } else {
    //        log.error("modbus", e);
    //      }
    //    });
    //ModbusMaster master = ModbusSlaveFactory.createSerialSlave(serialParams);
    //    try {
    //      modbus4j.init();
    //    } catch (ModbusInitException e) {
    //      throw new RuntimeException(e);
    //    }

    try {
      int timeout = Modbus.DEFAULT_TIMEOUT * 2;
      boolean reconnect = false;
      boolean useRtuOverTcp = false;
      j2mod = new ModbusTCPMaster(address, port, timeout, reconnect, useRtuOverTcp);
      j2mod.connect();
      j2mod.setRetries(10);
      j2mod.setCheckingValidity(true);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public int write(ModbusParam param) {
    try {
      switch (param.type.writeOne) {
        case F05_WRITE_COIL:
          return j2mod.writeCoil(unitId, param.address, param.modbusValue != 0) ? 1 : 0;
        case F06_WRITE_HOLDING_REGISTER:
          return j2mod.writeSingleRegister(unitId, param.address, new SimpleRegister(param.modbusValue));
        //        case F15_WRITE_COILS:
        //          break;
        //        case F16_WRITE_HOLDING_REGISTERS:
        //          break;
        default:
          throw new RuntimeException("Cannot write %s".formatted(param));
      }
    } catch (ModbusException e) {
      throw new RuntimeException(e);
    }
  }

  public int maxCount(ModbusType type) {
    int maxByteCount = Modbus.MAX_MESSAGE_LENGTH - 4;
    int maxCount = maxByteCount * 8 / type.bits;
    return maxCount;
  }

  public int[] read(ModbusType type, int address, int count) {
    int[] buffer = new int[count];
    int step = maxCount(type);
    Iterator.rangeBy(address, address + count, step).forEachWithIndex((x, index) -> {
      int end = Math.min(address + count, x + step);
      log.info("reading batch {}: {}->{}", type, x, end - 1);
      readOnce(buffer, index * step, type, x, end - x);
    });
    return buffer;
  }

  public int[] readOnce(int[] buffer, int index, ModbusType type, int address, int count) {
    int maxCount = maxCount(type);
    Preconditions.checkArgument(count <= maxCount,
      "Cannot read %s %ss . Maximum allowed is %s %ss (%s bits each, and max %s bytes allowed by modbus).",
      count, type, maxCount, type, type.bits, Modbus.MAX_MESSAGE_LENGTH - 6);
    try {
      switch (type.readMultiple) {
        case F01_READ_COILS:
          return toArray(buffer, index, j2mod.readCoils(unitId, address, count));
        case F02_READ_DISCRETE_INPUTS:
          return toArray(buffer, index, j2mod.readInputDiscretes(unitId, address, count));
        case F03_READ_HOLDING_REGISTER:
          return toArray(buffer, index, j2mod.readMultipleRegisters(unitId, address, count));
        case F04_READ_INPUT_REGISTERS:
          return toArray(buffer, index, j2mod.readInputRegisters(unitId, address, count));
        default:
          throw new RuntimeException("Cannot read %s@%s".formatted(type, address));
      }
    } catch (ModbusException e) {
      throw new RuntimeException(e);
    }
  }

  private int[] toArray(int[] buffer, int index, BitVector bits) {
    for (int i = 0; i < bits.size(); i++) {
      buffer[index + i] = bits.getBit(i) ? 1 : 0;
    }
    return buffer;
  }

  private int[] toArray(int[] buffer, int index, InputRegister[] registers) {
    for (int i = 0; i < registers.length; i++) {
      buffer[index + i] = registers[i].toShort();
    }
    return buffer;
  }

  public int readOld(ModbusParam param) {
    return readOld(param.type.readMultiple, param.address);
  }

  public int readOld(FunctionCodeExtension code, int offset) {
    try {
      int dataType = DataType.TWO_BYTE_INT_SIGNED;
      BaseLocator locator = null;
      if (code instanceof ModbusFunction c) {
        switch (c) {
          case F01_READ_COILS:
            locator = BaseLocator.coilStatus(unitId, offset);
            break;
          case F02_READ_DISCRETE_INPUTS:
            locator = BaseLocator.inputStatus(unitId, offset);
            break;
          case F03_READ_HOLDING_REGISTER:
            locator = BaseLocator.holdingRegister(unitId, offset, dataType);
            break;
          case F04_READ_INPUT_REGISTERS:
            locator = BaseLocator.inputRegister(unitId, offset, dataType);
            break;
          //case F17_REPORT_SLAVE_ID:
          //locator = new StringLocator(slaveId, code.code(), offset, DataType.CHAR, 1, StringLocator.ASCII);
          //locator = new StringLocator(slaveId, code.code(), offset, DataType.CHAR, 1, StringLocator.ASCII);
          //locator = new BinaryLocator(slaveId, code.code(), offset, 1);
          //break;
          case F07_READ_EXCEPTION_STATUS:
          case F08_DIAGNOSTICS:
          case F13_READ_DEVICE_IDENTIFICATION_MEI_TRANSPORT:
          case F14_READ_DEVICE_IDENTIFICATION:
            //locator = BaseLocator.createLocator(slaveId, code.code(), offset, dataType, offset, 2, StringLocator.ASCII);
            locator = new NumericLocator(unitId, code.code(), offset, dataType);
            break;
          default:
            if (c.readOperation) {
              locator = new NumericLocator(unitId, code.code(), offset, dataType);
            } else {
              throw new RuntimeException("Don't know how to execute function with code %s".formatted(code));
            }
        }
      } else {
        throw new RuntimeException("Don't know how to execute function with code %s".formatted(code));
      }
      Object returnValue = modbus4j.getValue(locator);
      if (returnValue instanceof Boolean b) {
        return b ? 1 : 0;
      }
      if (returnValue instanceof Short s) {
        return s;
      }
      return (int) returnValue;
    } catch (ModbusTransportException e) {
      throw new RuntimeException(e);
    } catch (ErrorResponseException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() throws Exception {
    if (modbus4j != null) {
      modbus4j.destroy();
    }
    j2mod.disconnect();
  }

  public static void main(String[] args) throws Exception {
    ModPollOptions modPollOptions = new ModPollOptions();
    int exitCode = new CommandLine(modPollOptions).execute("192.168.1.112".split("\s+"));
    System.exit(exitCode);
    //
    //    // ModPoll.main(new String[] {});
    //    // ModPoll.main("-a 1 -m2 tcp -p 8899 -t 1 -r 1 -c 125 -1 192.168.1.112".split("\s+"));
    //    read("--tcp 192.168.1.112 --tcp-port 8899 --config chofu3.csv".split("\s+"));
    //    List<String[]> config = readAllLines();
  }

  public static List<String[]> readAllLines(Path filePath) throws Exception {
    try (Reader reader = Files.newBufferedReader(filePath)) {
      try (CSVReader csvReader = new CSVReader(reader)) {
        return csvReader.readAll();
      }
    }
  }

  public static void a() throws Exception {
    String address = "192.168.1.112";
    int port = 8899;
    try (ModbusClient client = new ModbusClient(new Transport(new TcpUdp(address, port), null), 1)) {
      //int i = 11;
      //System.out.println("%s: %s".formatted(i, client.read(FunctionCode._04_READ_INPUT_REGISTERS, i)));

      Seq<ModbusFunction> readFunctions = API.Seq(
        //FunctionCode.values()).filter(x -> x.readOperation);
        //        FunctionCode.F01_READ_COILS,
        //        FunctionCode.F02_READ_DISCRETE_INPUTS,
        //        FunctionCode.F03_READ_HOLDING_REGISTER,
        //        FunctionCode.F04_READ_INPUT_REGISTERS
        //FunctionCode.F07_READ_EXCEPTION_STATUS); error:Unsupported function
        //FunctionCode.F08_DIAGNOSTICS, error:Unsupported function
        //FunctionCode.F11_GET_COM_EVENT_COUNTER, //error:Don't know how to execute function with code F11_GET_COM_EVENT_COUNTER
        //FunctionCode.F12_GET_COM_EVENT_LOG, //error:Don't know how to execute function with code F11_GET_COM_EVENT_COUNTER
        //FunctionCode.F13, error:Unsupported function
        //FunctionCode.F14, error:Unsupported function
        ModbusFunction.F17_REPORT_SLAVE_ID //error:Don't know how to execute function with code F11_GET_COM_EVENT_COUNTER
      //FunctionCode.F20_READ_FILE_RECORD, //error:Don't know how to execute function with code F20_READ_FILE_RECORD
      //FunctionCode.F23_READ_WRITE_MULTIPLE_REGISTERS,
      //FunctionCode.F24_READ_FIFO_QUEUE,
      //FunctionCode.F43_READ_DEVICE_IDENTIFICATION
      );
      readFunctions.forEach(f -> {
        for (int i = 0; i < 130; i++) {
          int i2 = i;
          Object value = Try.of(() -> client.readOld(f, i2))
            //          .map(x -> (boolean) x ? "1" : "0")
            //.getOrElseGet(x -> "error:" + ExceptionUtils.getRootCause(x).getMessage());
            .get();
          System.out.println("F%s\t%s\t%s".formatted(f.code, i, value));
          if (i == 0 && value.toString().startsWith("error:Unsupported")) {
            break;
          }
        }
      });
      System.out.println("end");
    }
  }
}
