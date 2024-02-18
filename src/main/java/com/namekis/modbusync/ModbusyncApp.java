package com.namekis.modbusync;

import java.util.List;

import ch.qos.logback.classic.Level;
import com.google.common.base.Splitter;
import com.namekis.modbusync.ModbusParam.ModbusType;
import io.vavr.collection.Iterator;
import io.vavr.collection.Map;
import io.vavr.collection.Traversable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringTokenizer;
import org.raisercostin.nodes.Nodes;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;

@Slf4j
public class ModbusyncApp implements AutoCloseable {
  public static class Test {
    public static void main(String[] args) throws Exception {
      //    int exitCode = cmd.execute(
      //      split(
      //        "-tcp=192.168.1.112 -p=8899 --read=holding,0,127 --read=coil,0,127 --read=input,0,127 --read=discrete,0,127"
      //            + " --output=./target/export-all.csv \"--config=./chofu mapping.xlsx - params.csv\" --force --debug"));
      ModbusyncApp.main(split(
        "-tcp=192.168.1.112 -p=8899 --read=holding,2,5,1 --read=coil,0,6 --read=input,0,7 --read=discrete,0,8 --read=holding,2,10"
            + " --output=./target/export2.csv \"--config=./chofu mapping.xlsx - params.csv\" --force --debug"));
    }
  }

  public static void main(String[] args) throws Exception {
    IExecutionExceptionHandler errorHandler = (ex, cmd, parseResult) -> {
      ModbusyncConfig config = cmd.getCommand();
      if (config.others != null && config.others.debug) {
        ex.printStackTrace(cmd.getErr()); // Print stack trace to the error stream
      } else {
        cmd.getErr()
          .println(
            cmd.getColorScheme()
              .errorText(ex.getMessage() + ". Use --debug to see stacktrace."));
      }
      cmd.getErr().println(cmd.getColorScheme().errorText("Use --help or -h for usage."));
      return cmd.getExitCodeExceptionMapper() != null
          ? cmd.getExitCodeExceptionMapper().getExitCode(ex)
          : cmd.getCommandSpec().exitCodeOnExecutionException();
    };

    CommandLine cmd = new CommandLine(new ModbusyncConfig()).setExecutionExceptionHandler(errorHandler);
    CommandLine gen = cmd.getSubcommands().get("generate-completion");
    gen.getCommandSpec().usageMessage().hidden(false);
    int exitCode = cmd.execute(args);
    System.exit(exitCode);
  }

  private static String[] split(String cmdWithSpaces) {
    StringTokenizer tokenizer = new StringTokenizer(cmdWithSpaces, ' ', '"');
    tokenizer.setIgnoreEmptyTokens(true);
    return tokenizer.getTokenArray();
  }

  private ModbusyncConfig config;
  private ModbusClient client;

  public ModbusyncApp(ModbusyncConfig config) {
    this.config = config;
    this.client = new ModbusClient(config.transport, config.unitId);
  }

  public ModbusParam read(ModbusParam param) {
    int value = client.read(param.type, param.address, 1)[0];
    ModbusParam res = param.setModbusValue(value);
    log.info("read {}", res);
    return res;
  }

  public ModbusParam write(ModbusParam param) {
    int readValue = client.write(param);
    ModbusParam res = param.setModbusValue(readValue);
    log.info("write {} => {}", param, readValue);
    return res;
  }

  public List<ModbusParam> readAll(ModbusParam... all) {
    return readAll(Iterator.of(all));
  }

  public List<ModbusParam> readAll(Iterable<ModbusParam> all) {
    return readAll(Iterator.ofAll(all));
  }

  public List<ModbusParam> readAll(Traversable<ModbusParam> all) {
    Map<ModbusType, ? extends Traversable<ModbusParam>> allGrouped = all.toList().groupBy(x -> x.type);
    return allGrouped.flatMap(params -> {
      ModbusParam min = params._2.minBy(x -> x.address).get();
      ModbusParam max = params._2.maxBy(x -> x.address).get();
      log.info("reading all {}: {}->{}", params._1, min.address, max.address);
      int[] values = client.read(params._1, min.address, max.address - min.address + 1);
      return params._2.map(p -> p.setModbusValue(values[p.address - min.address]));
    }).toJavaList();
  }

  @Override
  public void close() throws Exception {
    try {
      client.close();
    } finally {
      client = null;
    }
  }

  public Iterator<ModbusParam> backup(Map<String, ModbusParam> all) {
    return Iterator.of(config.reads).flatMap(read -> {
      Iterator<ModbusParam> params = Iterator.range(read.start(), read.start() + read.count())
        .map(
          x -> all.get(key(read.type(), x)).getOrElse(() -> ModbusParam.create().withType(read.type()).withAddress(x)));
      return readAll(params);
    });
  }

  private String key(ModbusType type, Integer address) {
    return "%s%s".formatted(type, address);
  }

  public void execute() {
    if (config.others != null && config.others.verbosity != null) {
      disableJ2ModLog("com.ghgande.j2mod", config.others.verbosity.logbackLevel);
    }
    // before anything try to write to file
    writeToFile("started", false);
    Map<String, ModbusParam> all = ModbusParam.csvMapper
      .toIterator(dropLines(config.config.readContent(), 2), ModbusParam.class)
      .toMap(x -> key(x.type, x.address), x -> x);
    //tojavalist since vavr Iterator else iterable is consumed on iteration
    String content = Nodes.csv.toString(backup(all).toJavaList());
    log.info("\n---\n{}", content);
    writeToFile(content, true);
  }

  private void writeToFile(String content, boolean forceWrite) {
    if (config.path != null) {
      if (!config.path.exists()) {
        config.path.write(content);
      } else {
        if (config.force || forceWrite) {
          config.path.write(content);
        } else {
          throw new RuntimeException(
            "Output " + config.path.toExternalForm() + " already exits. To overwrite use --force option.");
        }
      }
    }
  }

  private String dropLines(String content, int lines) {
    return Iterator.ofAll(Splitter.on('\n').splitToStream(content).iterator()).drop(lines).mkString("\n");
  }

  public static void disableJ2ModLog() {
    disableJ2ModLog("com.ghgande.j2mod", Level.OFF);
  }

  public static void disableJ2ModLog(String category, Level level) {
    ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
      .getLogger(category);
    logger.setLevel(level);
  }
}
