/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import scala.Tuple2;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.SparkSession;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class JavaWordCount {

  private static final Pattern SPACE = Pattern.compile(" +");
  //private static final Pattern SPACE = Pattern.compile("[\\n\\r\\s]+");

  public static void main(String[] args) throws Exception {

    System.setProperty("hadoop.home.dir", "C:\\winutil\\");

    if (args.length < 1) {
      System.err.println("Usage: JavaWordCount <file>");
      System.exit(1);
    }

    //Démarrage de Spark en Java
    SparkSession spark = SparkSession
            .builder()
            .appName("JavaWordCount")
            .config("spark.master", "local")
            .getOrCreate();

    //Parsing du .txt en JavaRDD pour travailler dessus
    JavaRDD<String> lines = spark.read().textFile(args[0]).javaRDD();

    //1 mots par ligne, on coupe sur les espaces, ensuite on retire les lignes vides
    JavaRDD<String> words = lines.flatMap(data -> Arrays.asList(SPACE.split(data)).iterator());
    words = words.filter(data -> !data.isEmpty());
    words.foreach(data -> data = data.replace("[.!?-]",""));

    words.foreach(data -> System.out.println("Mot : "+data));

    //1 occurence par mot
//    JavaPairRDD<String, Integer> ones = wordsNoLine.mapToPair(s -> new Tuple2<>(s, 1));
//    ones.foreach(data -> System.out.println("Mot : " + data._1() + " , Ocurrence :" + data._2()));
//
//    //Somme des clés
//    JavaPairRDD<String, Integer> counts = ones.reduceByKey((i1, i2) -> i1 + i2);
//    counts.foreach(data -> System.out.println("Mot : " + data._1() + " , Ocurrence :" + data._2()));

    //Filtrage des stopwords
//    JavaPairRDD<String, Integer> countsnostopwords = ones.filter(data -> !data._1.isEmpty());
//      countsnostopwords.foreach(data -> System.out.println("Mot : " + data._1() + " , Ocurrence :" + data._2()));

    //Récupère les 10 premiers
//    List<Tuple2<String, Integer>> topdix = counts.top(10);
    //topdix.forEach(data -> System.out.println("Mot : " + data._1() + " , Ocurrence :" + data._2()));

    spark.stop();
  }
}