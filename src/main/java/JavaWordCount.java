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

import org.spark_project.guava.collect.Ordering;
import scala.Serializable;
import scala.Tuple2;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.SparkSession;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class JavaWordCount {

  private static final Pattern SPACE = Pattern.compile(" +");
  //private static final Pattern SPACE = Pattern.compile("[\\n\\r\\s]+");

  private static class CustomComparator implements Serializable,Comparator {
      public int compare(Object number1, Object number2) {
          Tuple2<String, Integer> o1 = (Tuple2<String, Integer>) number1;
          Tuple2<String, Integer> o2 = (Tuple2<String, Integer>) number2;
          if (o1._2 > o2._2) { return o1._2; }
          else{ return o2._2; }
      }
  }

  public static void main(String[] args) throws Exception {

    System.setProperty("hadoop.home.dir", "C:\\winutil\\");

    //Démarrage de Spark en Java
    SparkSession spark = SparkSession
            .builder()
            .appName("JavaWordCount")
            .config("spark.master", "local")
            .getOrCreate();

    //Etape 1 : Parsing des .txt et des stopwords en JavaRDD pour travailler dessus
    JavaRDD<String> lines = spark.read().textFile("src/main/resources/cf/cf2010.txt").javaRDD(); //Pour l'instant sur un document, il suffit de changer en cf* ensuite
    JavaRDD<String> stopwords = spark.read().textFile("src/main/resources/french-stopwords.txt").javaRDD();

    //Etape 2: un mot par element, on sort les espaces, les lignes vides, puis la ponctuation
    JavaRDD<String> words = lines.flatMap(data -> Arrays.asList(SPACE.split(data)).iterator());
    words = words.filter(data -> !data.isEmpty());
    words.foreach(data -> data = data.replace(" |.|,|+|?|!|-|/",""));
    words.foreach(data -> System.out.println("Mot : "+data));

    System.out.println("Nombre de mots dans les data : "+words.count());
    //Etape 3 : Filtrage des stopwords (On le fais avant de finir l'étape 2 car plus rapide qu'avec les occurences et plus efficace)
    words = words.subtract(stopwords);
    System.out.println("Nombre de mots dans les data : "+words.count());

    //On associe une occurence a chaque mots pour compter ensuite
    JavaPairRDD<String, Integer> ones = words.mapToPair(s -> new Tuple2<>(s, 1));
    ones.foreach(data -> System.out.println("Mot :" + data._1() + " = " + data._2()));

    //On somme les occurences sur les clés
    JavaPairRDD<String, Integer> counts = ones.reduceByKey((i1, i2) -> i1 + i2);
    counts.foreach(data -> System.out.println("Mot :" + data._1() + " = " + data._2()));

    //Etape 4 : Récupèrer les 10 premiers
    // http://aseigneurin.github.io/2014/11/06/mapreduce-et-manipulations-par-cles-avec-apache-spark.html
    JavaPairRDD<Integer, String> reversed = counts.mapToPair(t -> new Tuple2<>(t._2, t._1));
    reversed = reversed.sortByKey(false);
    List<Tuple2<Integer, String>> reversedList = reversed.take(10);
    reversedList.forEach(data -> System.out.println("Mot : " + data._2 + " = " + data._1));

    spark.stop();
  }
}