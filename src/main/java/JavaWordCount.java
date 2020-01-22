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

import org.apache.commons.lang.StringUtils;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.rdd.RDD;
import org.apache.spark.sql.SparkSession;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.Map;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class JavaWordCount {

    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static String path = "";

    static void printUsage(){
        System.out.println("Usage : java JavaWordCount $1 where $X is :\n " +
                "\t $1 : cf or cp, the ressource folder used, required \n");
        System.exit(0);
    }

    public static void main(String[] args){

        if (args.length != 1 ){
            printUsage();
        }
        if (args[0].equalsIgnoreCase("cf")) path = "src/main/resources/cf/*";
        if (args[0].equalsIgnoreCase("cp")) path = "src/main/resources/cp/*";
        if (path.isEmpty()) printUsage();
        System.out.println("Going throught "+ path +" folder...");

        //Permet le fonctionnement de hadoop pour Windows
        System.setProperty("hadoop.home.dir", "C:\\winutil\\");

        //Démarrage de Spark en Java
        SparkSession spark = SparkSession
                .builder()
                .appName("JavaWordCount")
                .config("spark.master", "local")
                .getOrCreate();

        //Etape 1 : Parsing des .txt et des stopwords en JavaRDD pour travailler dessus
        JavaRDD<String> lines = spark.read().textFile(path).javaRDD();
        JavaRDD<String> stopwords = spark.read().textFile("src/main/resources/french-stopwords.txt").javaRDD();
        stopwords = stopwords.union(stopwords.map(StringUtils::capitalize));

        //Etape 2: un mot par element, on sort les espaces, les lignes vides, la ponctuation
        JavaRDD<String> words = lines.flatMap(data -> Arrays.asList(SPACE.split(data)).iterator());
        words = words.map(data -> data.replaceAll("[^/ éèàù’\\w]",""));
        words = words.filter(data -> !data.isEmpty());
        words = words.filter(data -> !data.matches("\\W|\\d") );

        //Etape 3 : Filtrage des stopwords (On le fais avant de finir l'étape 2 car plus rapide de travailler sur une liste de String que des Tuples)
        long before = words.count();
        words = words.subtract(stopwords);
        System.out.println("Il y avait "+ before +" éléments, les stop words ont retirés "+ (before-words.count())+" mots." );

        //On associe une occurence à chaque mots pour pouvoir comptabiliser
        JavaPairRDD<String, Integer> ones = words.mapToPair(s -> new Tuple2<>(s, 1));

        //On somme les occurences sur les clés
        JavaPairRDD<String, Integer> counts = ones.reduceByKey(Integer::sum);

        //Etape 4 : Récupèrer les 10 premiers
        JavaPairRDD<Integer, String> reversed = counts.mapToPair(t -> new Tuple2<>(t._2, t._1));
        reversed = reversed.sortByKey(false);
        List<Tuple2<Integer, String>> reversedList = reversed.take(10);
        reversedList.forEach(data -> System.out.println("Mot : " + data._2 + " = " + data._1));

        //Nettoie les rdd utilisées
        Map<Object, RDD<?>> persistentRDDS = spark.sparkContext().getPersistentRDDs();
        for (Iterator<RDD<?>> iterator = persistentRDDS.values().iterator(); iterator.hasNext();)
            iterator.next().unpersist(true);
        spark.stop();
    }

}
