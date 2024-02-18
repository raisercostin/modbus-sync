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
import com.opencsv.CSVReader;
import com.serotonin.modbus4j.code.DataType;
import com.serotonin.modbus4j.exception.ErrorResponseException;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.locator.BaseLocator;
import com.serotonin.modbus4j.locator.NumericLocator;
import io.vavr.collection.Iterator;

public class ModbusClient implements AutoCloseable {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ModbusClient.class);

  interface FunctionCodeExtension {
    int code();
  }

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

  @Override
  public void close() throws Exception {
    j2mod.disconnect();
  }

  public static List<String[]> readAllLines(Path filePath) throws Exception {
    try (Reader reader = Files.newBufferedReader(filePath)) {
      try (CSVReader csvReader = new CSVReader(reader)) {
        return csvReader.readAll();
      }
    }
  }
}
