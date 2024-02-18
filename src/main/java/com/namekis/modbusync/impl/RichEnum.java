package com.namekis.modbusync.impl;

import io.vavr.Function1;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Iterator;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;

public class RichEnum {
  public static <K, T extends Enum<T>> Map<K, T> cacheByIds(Class<T> clazz, Function1<T, K> idExtractor) {
    Vector<Tuple2<K, T>> all = Iterator.of(clazz.getEnumConstants())
      .map(x -> Tuple.of(idExtractor.apply(x), x))
      .toVector();
    Seq<Tuple2<K, T>> valuesWithDuplicateCodes = all.iterator()
      .groupBy(x -> x._1)
      .filter(x -> x._2.size() > 1)
      .flatMap(x -> x._2)
      .toList();
    if (valuesWithDuplicateCodes.nonEmpty()) {
      throw new RuntimeException(
        "Enumeration " + clazz + " has several values with same code on mapper function: " + valuesWithDuplicateCodes);
    }
    return all.toMap(x -> x);
  }
}
