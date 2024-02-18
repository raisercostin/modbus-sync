# ModbuSync

Modbus sync: backup, restore, diff in java.
Full parameters defined in csv/tsv.

## Rationale

Someone that needs to control a modbus device will have a hard time understanding how these works.

This software will help on:

- see and export current values: backup
- convert modbus parameters in a human readable format: scaling, offseting, stepping values, min/max values, writable, measure unit, description, possible values, etc
- import values: restore
- diff between exports
- advices ?
- database of devices
- export to be used in [home assistant](https://www.home-assistant.io/)
- export to be used in [openhab](https://www.openhab.org/)

## Usage

Read from a modbus tcp server holding,coil,input,discrete params each from address 0 to 127. Export all params to a csv and see more details on parameters in given config file.

```shell
modbusync -tcp=192.168.1.112 -p=8899 --read=holding,0,127 --read=coil,0,127 --read=input,0,127 --read=discrete,0,127 --output=./target/export-all.csv "--config=./chofu mapping.xlsx - params.csv" --force --debug
```

## Help

```shell
Usage: modbusync [-fhV] [-c=<config>] [-o=<path>] [-u=<unitId>] -r=<reads>
                 [-r=<reads>]... ([-tcp=<host> [-p=<port>]] |
                 [-serial=<serialPort> [-b=<baudrate>] [-d=<databits>]
                 [-s=<stopbits>] [-parity=<parity>]]) [[-v=<verbosity>]
                 [--debug]] [COMMAND]
Synchornize backup/restore MODBUS devices.
  -c, --config=<config>      Parameters details. Manually change an output file
                               to add them
  -f, --force                Overwrite output file if already exists
                               Default: false
  -h, --help                 Show this help message and exit.
  -o, --output=<path>        File to write csv
  -r, --read=<reads>         Read operations in the format Type,Start,Count,
                               MaxBatch. Example: COIL,0,10[,130] .
                               Type - coil,discrete,holding,input
                                 - coil - read-write binary    - F1, F5, F15
                                 - discrete - read binary      - F2
                                 - holding - read write 2bytes - F3, F6, F16
                                 - input - read 2bytes         - F4
                               Start - start address
                               Count - number of params to read
                               MaxBatch - max number of addresses read in one
                               batch. The default is the around 127bytes.

  -u, -unitid=<unitId>       Unit id or slave id
                               Default: 1
  -V, --version              Print version information and exit.

Transport: Tcp
  -p=<port>                  IP protocol port number. Default: 502.
      -tcp=<host>            Host name/IP for MODBUS/TCP.

Transport: Serial
  -b, -baudrate=<baudrate>   Baudrate. Default: 19200.
  -d, -databits=<databits>   Databits (7 or 8 for ASCII, 8 for RTU). Default: 8.
      -parity=<parity>       Parity (none, even, odd). Default: none.
  -s, -stopbits=<stopbits>   Stopbits (1 or 2). Default: 1.
      -serial=<serialPort>   Serial port when using Modbus ASCII/RTU

Others:
      --debug                Show stack trace
  -v, --verbosity=<verbosity>
                             Set the verbosity level: NONE, ERROR, WARN, INFO,
                               DEBUG, TRACE.
                               Default: INFO
Commands:
  generate-completion  Generate bash/zsh completion script for modbusync.
```

## TODO

- write
- write multiple
- diff
- sort by a column
- hobby
  - modpoll interface using picocli

## History

### 2024-02-18

- Readme usage, todo, history
- Column for read/read write with default value based on parameter type: coil(rw), discrete(r), holding(rw), input(r)
  - This will help a new commer to understand what values can be changed
  - Some fields are in coil and register but will still be readonly for a device. Writes for such fields will not be attempted.

### 2024-02-17

- initial version
