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

import org.apache.spark.ml.feature.StopWordsRemover;
import org.apache.spark.ml.fpm.FPGrowth;
import org.apache.spark.ml.fpm.FPGrowthModel;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JavaWordMining {

    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final double minsup = 0.5;
    private static final double minconf = 0.5;

    public static void main(String[] args) throws IOException {

        System.setProperty("hadoop.home.dir", "C:\\winutil\\");

        //Démarrage de Spark en Java
        SparkSession spark = SparkSession
                .builder()
                .appName("JavaWordCount")
                .config("spark.master", "local")
                .getOrCreate();

        //Etape 2: Filtrage des stopwords dans chaques transactions
        String load = new String(Files.readAllBytes(Paths.get("src/main/resources/french-stopwords.txt")));
        //Transformation en tableau de string pour chaque mot
        List<String> stopwords = Arrays.asList(SPACE.split(load));

        //Classe faite pour retirer les stopwords
        StopWordsRemover remover = new StopWordsRemover();
        remover.setStopWords((String[]) stopwords.toArray());
        //Définition de la colonne à modifier
        remover.setInputCol("items");
        //Définition de la colonne crée à la fin
        remover.setOutputCol("new");

        String rep = "";

        Scanner sc = new Scanner(System.in);

        while (!rep.equals("1") && !rep.equals("2")) {

            System.out.println("Faite votre choix :");
            System.out.println("1 - Fichiers cp");
            System.out.println("2 - Fichiers cf");

            rep = sc.nextLine();
        }
        
        if(rep.equals("1")) {
            File cp = new File("src/main/resources/cp");

            wordMining(cp,spark,remover);
        } else {
            File cf = new File("src/main/resources/cf");

            wordMining(cf,spark,remover);
        }

        SparkSession.clearActiveSession();
        spark.stop();
    }

    static void wordMining(File folder, SparkSession spark, StopWordsRemover remover) throws IOException {

        ArrayList<Row> tab = new ArrayList<>();
        for (File f: Objects.requireNonNull(folder.listFiles())) {
            String load = new String(Files.readAllBytes(Paths.get(f.getPath())));
            //Remplacement des caractère ne faisant pas partie des mots
            load = load.replaceAll("[^/ éèàù’\\w]","").replaceAll("\\d","");
            //Ajout à la liste des transactions
            tab.add(RowFactory.create(Arrays.stream(SPACE.split(load)).distinct().collect(Collectors.toList()))) ;
        }

        StructType schema = new StructType(new StructField[]{ new StructField(
                "items", new ArrayType(DataTypes.StringType, true), false, Metadata.empty())
        });
        //Création du Dataset à partir du tableau de transaction et du schéma
        Dataset<Row> lines = spark.createDataFrame(tab, schema);

        //Application du remover sur le Dataset
        lines = remover.transform(lines);

        //Drop de l'ancienne colonne
        lines = lines.drop("items");



        String rep = "";

        Scanner sc = new Scanner(System.in);

        while (!rep.equals("1") && !rep.equals("2")) {

            System.out.println("Faite votre choix :");
            System.out.println("1 - Min support");
            System.out.println("2 - Min Confidence");

            rep = sc.nextLine();
        }

        float value = -1;

        while (value <= 0 || value > 1  ) {

            System.out.println("Entrez une valeur supérieure à 0 et 1 :");

            String  r = sc.nextLine();

            try {
                value = Float.parseFloat(r);
            } catch (NumberFormatException e) {
            }

        }

        int nbLigne = -1;

        while( nbLigne <= 0) {

            System.out.println("Nombre de lignes à afficher (supérieur à 0) :");

            String  r = sc.nextLine();

            try {
                nbLigne = Integer.parseInt(r);
            } catch (NumberFormatException e) {
            }
        }

        FPGrowthModel model;

        if(rep.equals("1")) {
            //Application de FPGrowth
            model = new FPGrowth()
                    .setItemsCol("new")
                    .setMinSupport(value)
                    .fit(lines);
            model.freqItemsets().show(nbLigne,false);

        } else {
            //Application de FPGrowth
            model = new FPGrowth()
                    .setItemsCol("new")
                    .setMinConfidence(value)
                    .fit(lines);

            model.associationRules().show(false);

        }

        //Affichage.
    }

}
