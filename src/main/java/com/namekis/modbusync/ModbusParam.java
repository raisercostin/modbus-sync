package com.namekis.modbusync;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.google.common.base.Preconditions;
import com.namekis.modbusync.ModbusClient.FunctionCodeExtension;
import com.namekis.modbusync.impl.PostConstructConverter;
import com.namekis.modbusync.impl.PostConstructConverter.PostConstruct;
import com.namekis.modbusync.impl.RichEnum;
import io.vavr.collection.Map;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.raisercostin.nodes.Nodes;
import org.raisercostin.nodes.impl.CsvNodes;

/**

 */

@With
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
@ToString
@JsonDeserialize(converter = ModbusParam.PostConstructor2.class)
public class ModbusParam {
  public static final CsvNodes csvMapper = createMapper();

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD) // Enum constants are considered fields
  public @interface UnknownValue {}

  //Sub-class of PostConstructConverter to parameterise it with Example
  private static class PostConstructor2 extends PostConstructConverter<ModbusParam> {

  }

  public interface CodeEnum {
    Object getCode();
  }

  public static class CodeEnumDeserializer<E extends Enum<E> & CodeEnum> extends JsonDeserializer<E>
      implements ContextualDeserializer {
    private final Class<E> enumClass;
    private final Map<Object, E> BY_CODES;
    private final E unknown;

    public CodeEnumDeserializer() {
      this.enumClass = null;
      this.BY_CODES = null;
      this.unknown = null;
    }

    public CodeEnumDeserializer(Class<E> enumClass) {
      this.enumClass = enumClass;
      this.BY_CODES = RichEnum.cacheByIds(enumClass, x -> x.getCode());
      this.unknown = this.BY_CODES.find(x -> {
        try {
          Field field = enumClass.getField(x._2().name());
          UnknownValue annotation = field.getAnnotation(UnknownValue.class);
          return annotation != null;
        } catch (NoSuchFieldException | SecurityException e) {
          throw new RuntimeException(e);
        }
      }).map(x -> x._2).getOrNull();
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
        throws JsonMappingException {
      Class<E> enumClass2 = (Class<E>) ctxt.getContextualType().getRawClass();
      return new CodeEnumDeserializer(enumClass2);
    }

    @Override
    public E deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
      Object code = p.getText();
      return fromCode(code);
    }

    private E fromCode(Object code) {
      E res = BY_CODES.get(code).getOrNull();
      if (res != null) {
        return res;
      }
      if (unknown != null) {
        return unknown;
      }
      throw new IllegalArgumentException(
        "Unknown code [%s] for enum %s. You can annotate one enum value with @UnknownValue and that would be returned."
          .formatted(code, enumClass.getSimpleName()));
    }
  }

  @JsonDeserialize(using = CodeEnumDeserializer.class)
  public enum Level implements CodeEnum {
    @UnknownValue
    User("U"),
    Installer("I"),
    Service("S");

    public String code;

    Level(String code) {
      this.code = code;
    }

    @Override
    public Object getCode() {
      return code;
    }
  }

  /*
   * - 90-99 and others Custom or Proprietary Function Codes
   * Many Modbus devices support custom function codes for device-specific features not covered by the standard Modbus
   * functions. These are often documented by the device manufacturer and can vary widely between devices.
   * - 100-110 - Reserved for future expansion of the standard.
   * - >127 - Function codes above 127 (0x7F) are reserved for exception responses. When a device returns an exception,
   * it responds with the requested function code plus 128. For example, if you request function code 3 and there's an
   * exception, the device might respond with function code 131 (3 + 128).
   */
  //ModbusFunction
  public enum ModbusFunction implements FunctionCodeExtension {
    F01_READ_COILS(com.serotonin.modbus4j.code.FunctionCode.READ_COILS,
        "Read multiple coils",
        "Read the ON/OFF status of discrete outputs (coils).", true),
    F02_READ_DISCRETE_INPUTS(com.serotonin.modbus4j.code.FunctionCode.READ_DISCRETE_INPUTS,
        "Read multiple discrete inputs",
        "Read the ON/OFF status of discrete inputs.", true),
    F03_READ_HOLDING_REGISTER(com.serotonin.modbus4j.code.FunctionCode.READ_HOLDING_REGISTERS,
        "Read multiple registers",
        "Read the binary contents of holding registers.", true),
    F04_READ_INPUT_REGISTERS(com.serotonin.modbus4j.code.FunctionCode.READ_INPUT_REGISTERS,
        "Read multiple input registers",
        "Read the binary contents of input registers.", true),
    F05_WRITE_COIL(com.serotonin.modbus4j.code.FunctionCode.WRITE_COIL,
        "Write single coil",
        "Write a single coil to either ON or OFF."),
    F06_WRITE_HOLDING_REGISTER(com.serotonin.modbus4j.code.FunctionCode.WRITE_REGISTER,
        "Write single register",
        "Write a single holding register."),
    F07_READ_EXCEPTION_STATUS(com.serotonin.modbus4j.code.FunctionCode.READ_EXCEPTION_STATUS,
        "Read the status of eight Exception Status outputs in a remote device.", true),
    F08_DIAGNOSTICS(8, "Perform diagnostic operations on the Modbus network.", true),
    F11_GET_COM_EVENT_COUNTER(11, "Read the communication event counter from the remote device.", true),
    F12_GET_COM_EVENT_LOG(12, "Read the communication event log from the remote device.", true),
    F13_READ_DEVICE_IDENTIFICATION_MEI_TRANSPORT(13, "More comprehensive device identification mechanism.", true),
    F14_READ_DEVICE_IDENTIFICATION(14, "Access to the device identification and additional information. See also 43",
        true),
    F15_WRITE_COILS(com.serotonin.modbus4j.code.FunctionCode.WRITE_COILS,
        "Write multiple coils",
        "Write multiple coils in a sequence."),
    F16_WRITE_HOLDING_REGISTERS(com.serotonin.modbus4j.code.FunctionCode.WRITE_REGISTERS,
        "Write multiple registers",
        "Write multiple holding registers in a sequence."),
    F17_REPORT_SLAVE_ID(com.serotonin.modbus4j.code.FunctionCode.REPORT_SLAVE_ID,
        "Report the server (slave) identity.", true),
    F20_READ_FILE_RECORD(20, "Read File Record, for accessing the device's file system.", true),
    F21_WRITE_FILE_RECORD(21, "Write File Record, for accessing the device's file system.", false),
    F22_WRITE_MASK_REGISTER(com.serotonin.modbus4j.code.FunctionCode.WRITE_MASK_REGISTER,
        "Modify the contents of a holding register by applying bitwise AND and OR with the current value and the provided data.",
        false),
    F23_READ_WRITE_MULTIPLE_REGISTERS(23,
        "Read from holding registers and write to holding registers in a single atomic operation.", false),
    F24_READ_FIFO_QUEUE(24, "Read the contents of a First-In-First-Out (FIFO) queue of registers.", true),
    F43_READ_DEVICE_IDENTIFICATION(43, "Access to the device identification and additional information.", true);

    public final int code;
    public final String name;
    public final String description;
    public final boolean readOperation;

    ModbusFunction(int code, String name, String description) {
      this(code, name, description, false);
    }

    ModbusFunction(int code, String name, String description, boolean readOperation) {
      this.code = code;
      this.name = name;
      this.description = description;
      this.readOperation = readOperation;
    }

    ModbusFunction(int code, String name, boolean readOperation) {
      this.code = code;
      this.name = name;
      this.description = name;
      this.readOperation = readOperation;
    }

    public int getCode() {
      return code;
    }

    @Override
    public int code() {
      return this.code;
    }
  }

  public enum ModbusType {
    coil("coil", 1, ModbusFunction.F01_READ_COILS, ModbusFunction.F05_WRITE_COIL, ModbusFunction.F15_WRITE_COILS,
        ModbusDataType.bool),
    discrete("discrete", 1, ModbusFunction.F02_READ_DISCRETE_INPUTS, null, null, ModbusDataType.bool),
    holding("register", 16, ModbusFunction.F03_READ_HOLDING_REGISTER, ModbusFunction.F06_WRITE_HOLDING_REGISTER,
        ModbusFunction.F16_WRITE_HOLDING_REGISTERS, ModbusDataType.uint16),
    input("input", 16, ModbusFunction.F04_READ_INPUT_REGISTERS, null, null, ModbusDataType.uint16);

    public String code;
    public int bits;
    public ModbusFunction readMultiple;
    public ModbusFunction writeOne;
    public ModbusFunction writeMany;
    public ModbusDataType defaultDataType;

    ModbusType(String code, int bits, ModbusFunction readMultiple, ModbusFunction writeOne, ModbusFunction writeMany,
        ModbusDataType defaultDataType)
    {
      this.code = code;
      this.bits = bits;
      this.readMultiple = readMultiple;
      this.writeOne = writeOne;
      this.writeMany = writeMany;
      this.defaultDataType = defaultDataType;
    }

    boolean isWritable() {
      return writeOne != null || writeMany != null;
    }
  }

  @JsonDeserialize(using = CodeEnumDeserializer.class) // Link the custom deserializer
  public enum ModbusDataType implements CodeEnum {
    bool,
    uint16,
    @UnknownValue
    unknown;

    @Override
    public Object getCode() {
      return this;
    }
  }

  public static ModbusParam create() {
    return new ModbusParam();
  }

  private static CsvNodes createMapper() {
    CsvNodes mapper = Nodes.csv
      .withMapper(x -> {
        x.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
          .addHandler(
            new DeserializationProblemHandler()
              {
                @Override
                public Object handleWeirdStringValue(DeserializationContext ctxt, Class<?> targetType,
                    String valueToConvert, String failureMsg) {
                  return null;
                }
              });
        x.coercionConfigFor(LogicalType.Enum)
          .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);
        return x;
      });
    return mapper;
  }

  /**
   * A parameter should always start with P in order to be easily searchable in code or documents.
   * This should be a unique identifier.
   * Example: P0100, P0101
   */
  @JsonProperty(index = 6)
  public String param;
  /**
   * The name of the parameter group.
   * A device will group that parameters by a name and a code prefix in param.
   * - 01 : Read value of conditions and settings (Read only)
   * - 02 : Remote controller
   * - 03 : Heat pump unit
   */
  public String group;
  /**
   * The parameter is configurable by User, Installer, Service. Each device decides how it controlls access to these.
   * Modbus doesn't have any security mechanism.
   */
  public Level level;
  /**
   * A short name for the parameter.
   * - Return water temperature
   * - DHW mode
   */
  @ToString.Include(rank = 10)
  @JsonProperty(index = 7)
  public String name;
  /**
   * A description of the parameter.
   * - Return water temperature
   * - Selected Domestic Hot Water operating mode
   */
  @ToString.Include(rank = -1)
  public String description;
  /**
   * Values of the parameter.
   * -
   * - 0=disable, 1=Comfort, 2=Economy, 3=Force
   */
  @ToString.Include(rank = -2)
  public String values;
  @JsonProperty("Default")
  @ToString.Include(rank = 3)
  public String defaultValue;
  @JsonProperty("Min")
  @ToString.Include(rank = 2)
  public String minValue;
  @JsonProperty("Max")
  @ToString.Include(rank = 1)
  public String maxValue;
  @ToString.Include(rank = 0)
  public String remarks;
  @ToString.Include(rank = 99)
  @JsonProperty(index = 5)
  public String unit;
  /**
   * The step that the value can be grown.
   */
  public String step;
  /**
   * Number of valid decimals. Default 0.
   */
  public Integer precision;
  /**
   * The value is computed with value = modbusValue * scale + offset
   */
  public BigDecimal offset;
  public BigDecimal scale;
  @ToString.Include(rank = 100)
  @JsonProperty(index = 4)
  public Number value;
  //Modbus Param
  @ToString.Include(rank = 9)
  @JsonProperty(index = 1)
  public ModbusType type;
  @ToString.Include(rank = 8)
  @JsonProperty(index = 2)
  public int address;
  public ModbusDataType dataType;
  @ToString.Include(rank = 7)
  @JsonProperty(index = 3)
  public Integer modbusValue;

  @PostConstruct
  private void postConstruct() {
    //Normally we change the value and we must compute modbusValue from this one
    setValueInternal(value);
  }

  @JsonAnySetter
  public void setField(String field, Object value) {
    log.info("ignoring [{}]:[{}]", field, value);
  }

  public ModbusParam setModbusValue(boolean value) {
    asBinaryCoil();
    return setModbusValue(value ? 1 : 0);
  }

  public ModbusParam setValue(String value) {
    return setValue(new BigDecimal(value));
  }

  /**Sets parameter value.*/
  public ModbusParam setValue(Number value) {
    asWritableRegistry();
    return copy().setValueInternal(value);
  }

  private ModbusParam copy() {
    return withAddress(address + 1).withAddress(address);
  }

  private ModbusParam setValueInternal(Number value) {
    this.value = value;
    this.modbusValue = unscale(value);
    return this;
  }

  /**Sets directly in modbus terminology.*/
  public ModbusParam setModbusValue(int modbusValue) {
    ModbusParam this2 = copy();
    this2.modbusValue = modbusValue;
    this2.value = scale(modbusValue);
    return this2;
  }

  private Integer unscale(Number value) {
    if (value == null) {
      return null;
    }
    BigDecimal res = new BigDecimal(value.toString());
    if (offset != null) {
      res = res.subtract(offset);
    }
    if (scale != null) {
      res = res.divide(scale);
    }
    return res.intValueExact();
  }

  private Number scale(int value) {
    BigDecimal res = new BigDecimal(value);
    if (scale != null) {
      res = res.multiply(scale);
    }
    if (offset != null) {
      res = res.add(offset);
    }
    if (res.stripTrailingZeros().scale() <= 0) {
      return res.intValueExact();
    }
    return res;
  }

  public boolean isWritable() {
    return type.isWritable();
  }

  public ModbusParam enable() {
    return setModbusValue(true);
  }

  public ModbusParam disable() {
    return setModbusValue(false);
  }

  public ModbusParam setValue(boolean value) {
    return setModbusValue(value);
  }

  public ModbusParam asWritableRegistry() {
    Preconditions.checkArgument(type.isWritable(), "Param %s should be writable.", type);
    return this;
  }

  public ModbusParam asHoldingRegister() {
    Preconditions.checkArgument(type == ModbusType.holding, "Param %s should be %s.", type, ModbusType.holding);
    return this;
  }

  public ModbusParam asReadOnlyBinaryDiscreteInput() {
    Preconditions.checkArgument(type == ModbusType.discrete, "Param %s should be %s.", type, ModbusType.discrete);
    return this;
  }

  public ModbusParam asBinaryCoil() {
    Preconditions.checkArgument(type == ModbusType.coil, "Param %s should be %s.", type, ModbusType.coil);
    return this;
  }

  public ModbusParam asReadOnlyInputRegistry() {
    Preconditions.checkArgument(type == ModbusType.input, "Param %s should be %s.", type, ModbusType.input);
    return this;
  }
}
