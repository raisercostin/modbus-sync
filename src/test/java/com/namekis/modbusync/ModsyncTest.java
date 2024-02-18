package com.namekis.modbusync;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.MapperFeature;
import com.namekis.modbusync.ModbusParam.ModbusType;
import com.namekis.modbusync.ModbusyncConfig.ModbusRead;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.raisercostin.jedio.Locations;
import org.raisercostin.jedio.path.PathLocation;
import org.raisercostin.nodes.Nodes;
import org.raisercostin.nodes.impl.CsvNodes;

@Slf4j
public class ModsyncTest {

  @Test
  void testReadingWritingParams() {
    ModbusyncApp.disableJ2ModLog();

    String paramsCsv = """
        "Param","Group","Level","Param","Description","Default","Min","Max","Unit","Remarks","","ModbusValue","Value","Type","Function","Address","Name","Code","Param","Step","Scale","Offset","Precision","DataType","Unit","Default","Min","Max","Remarks","","Read/Write","Modbus","","Address","F1-Coil-RW","F2-discrete-inputs","F3-Holding Register RW","F4-Input Register R","","ModbusValue","F1-Coil-RW","F2-Discrete-Inputs R","F3 Holding Register RW","F4 Input Register R",""
        "P0100","01 : Read value of conditions and settings (Read only)","U","01 00","Return water temperature","-","-20","100","1°C","monitor display PCB d0","-","40","40","input","F4","0","P0100U Return Water Temperature","P0100U-ReturnWaterTemperature","P0100U","1","","","","","°C","-","-20","100","monitor display PCB d0","","","0","","0","","","","0","","40","","","","40",""
        "P2110","","I","21 10","Heating Zone2, enable Outgoing water set point","0","0","1","-","0=Fixed set point, 1=Climatic curve enabled","-","0","0","coil","F1","3","P2110I Heating Zone2 Enable Outgoing Water Set Point","P2110I-HeatingZone2EnableOutgoingWaterSetPoint","P2110I","","","","","","-","0","0","1","0=Fixed set point, 1=Climatic curve enabled","","","3","","3","3","","","","","0","0","","","",""
        "P0116","","U","01 16","Heating/Cooling time bands setting Zone1: 0=disable, 1=active (Comfort or Economy)","0","0","1","-","^","-","0","0","discrete","F2","0","P0116U HeatingCooling Time Bands Setting Zone1 0Disable 1Active Comfort Or Economy","P0116U-HeatingCoolingTimeBandsSettingZone10Disable1ActiveComfortOrEconomy","P0116U","","","","","","-","0","0","1","^","","","0","","0","","0","","","","0","","0","","",""
        "P2111","","I","21 11","Heating Zone2, Fixed Outgoing water set point in Heating","45","23","60","0.5°C","","-","500","50","holding","F3","7","P2111I Heating Zone2 Fixed Outgoing Water Set Point In Heating","P2111I-HeatingZone2FixedOutgoingWaterSetPointInHeating","P2111I","0.5","0.1","","","uint16","°C","45","23","60","","","","7","","7","","","7","","","500","","","500"
        """;

    List<ModbusParam> all = ModbusParam.csvMapper.toList(paramsCsv, ModbusParam.class);
    ModbusyncApp app = new ModbusyncApp(ModbusyncConfig.tcp("192.168.1.112", 8899, 1));

    ModbusParam param0 = all.get(0).asReadOnlyInputRegistry();
    assertThat(param0.type).isEqualTo(ModbusType.input);
    assertThat(param0.value).isEqualTo(40);
    assertThat(param0.modbusValue).isEqualTo(40);
    assertThat(param0.isWritable()).isFalse();
    ModbusParam param0Read = app.read(param0);
    assertThat(new BigDecimal(param0Read.value.toString())).isGreaterThan(new BigDecimal(5));

    ModbusParam param1 = all.get(1).asBinaryCoil();
    assertThat(param1.type).isEqualTo(ModbusType.coil);
    assertThat(param1.value).isEqualTo(0);
    assertThat(param1.modbusValue).isEqualTo(0);
    assertThat(param1.isWritable()).isTrue();
    ModbusParam param1Read = app.read(param1);
    ModbusParam param1Write1 = app.write(param1.enable());
    assertThat(param1Write1.value).isEqualTo(1);
    ModbusParam param1Write2 = app.write(param1.disable());
    assertThat(param1Write2.value).isEqualTo(0);
    ModbusParam param1Write3 = app.write(param1.setValue(param1Read.value));
    assertThat(param1Write3.value).isEqualTo(param1Read.value);

    ModbusParam param2 = all.get(2).asReadOnlyBinaryDiscreteInput();
    assertThat(param2.type).isEqualTo(ModbusType.discrete);
    assertThat(param2.value).isEqualTo(0);
    assertThat(param2.modbusValue).isEqualTo(0);
    assertThat(param2.isWritable()).isFalse();
    ModbusParam param2Read = app.read(param2);
    assertThat(param2Read.value).isEqualTo(0);

    ModbusParam param3 = all.get(3).asHoldingRegister();
    assertThat(param3.type).isEqualTo(ModbusType.holding);
    assertThat(param3.value).isEqualTo(50);
    assertThat(param3.modbusValue).isEqualTo(500);
    assertThat(param3.isWritable()).isTrue();
    ModbusParam param3Read = app.read(param3);
    //assertThat(param3Read.value).isEqualTo(45);
    ModbusParam param3Write1 = app.write(param3.setValue(39));
    assertThat(param3Write1.value).isEqualTo(39);
    ModbusParam param3Write2 = app.write(param3.setValue("38.5"));
    assertThat(param3Write2.value).isEqualTo(new BigDecimal("38.5"));
    ModbusParam param3Write3 = app.write(param3Read.setValue(45));
    //assertThat(param3Write3.value).isEqualTo(param3Read.value);
    assertThat(param3Write3.value).isEqualTo(45);

    app.readAll(all);
    //    app.execute(ModsyncConfig
    //      .tcp("192.168.1.112", 8899)
    //      .withParams(all));
  }

  @Test
  void testBackup() {
    //disableJ2ModLog();
    PathLocation path = Locations.current().child("export.csv");
    ModbusyncApp app = new ModbusyncApp(
      ModbusyncConfig
        .tcp("192.168.1.112", 8899, 1)
        .withReads(
          new ModbusRead(ModbusType.holding, 0, 127),
          new ModbusRead(ModbusType.coil, 0, 127),
          new ModbusRead(ModbusType.discrete, 0, 127),
          new ModbusRead(ModbusType.input, 0, 127))
        .withPath(path));
    app.execute();
  }
}
