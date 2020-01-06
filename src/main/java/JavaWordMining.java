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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.spark.ml.feature.StopWordsRemover;
import org.apache.spark.ml.fpm.FPGrowth;
import org.apache.spark.ml.fpm.FPGrowthModel;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.*;

public final class JavaWordMining {
    private static final Pattern SPACE = Pattern.compile("\\s+");

    public static void main(String[] args) throws IOException {

        System.setProperty("hadoop.home.dir", "C:\\winutil\\");

        //Démarrage de Spark en Java
        SparkSession spark = SparkSession
                .builder()
                .appName("JavaWordCount")
                .config("spark.master", "local")
                .getOrCreate();

        //Récupération du dossier contenant tous les fichiers
        File folder = new File("src/main/resources/cf");

        ArrayList<Row> tab = new ArrayList<>();

        //Parcours de chaque fichier
        for (File f: folder.listFiles()) {
            //Transformation du fichier en string
            String load = new String(Files.readAllBytes(Paths.get(f.getPath())));

            //Remplacement des caractère ne faisant pas partie des mots
            load = load.replaceAll("[^\\/ éèàù’\\w]","");

            //Ajout à la liste des transactions
            tab.add(RowFactory.create(Arrays.asList(SPACE.split(load)).stream().distinct().collect(Collectors.toList()))) ;
        }

        //Transformation en string du fichier de stopwords
        String load = new String(Files.readAllBytes(Paths.get("src/main/resources/french-stopwords.txt")));

        //Transformation en tableau de string pour chaque mot
        List<String> stop = Arrays.asList(SPACE.split(load));

        StructType schema = new StructType(new StructField[]{ new StructField(
                "items", new ArrayType(DataTypes.StringType, true), false, Metadata.empty())
        });

        //Création du Dataset à partir du tableau de transaction et du schéma
        Dataset<Row> lines = spark.createDataFrame(tab, schema);

        //Création du remover afin d'enlever les mots présent dans le fichier stopwords
        StopWordsRemover remove = new StopWordsRemover();
        //récupération des mots interdits
        remove.setStopWords((String[]) stop.toArray());
        //Récupération de la colonne à modifier
        remove.setInputCol("items");
        //Colonne créer à la fin
        remove.setOutputCol("new");

        //Application du remover sur le Dataset
        lines = remove.transform(lines);

        //Drop de l'ancienne colonne
        lines = lines.drop("items");

        //Application des algorithmes
        FPGrowthModel model = new FPGrowth()
                .setItemsCol("new")
                .setMinSupport(0.5)
                .setMinConfidence(0.2)
                .fit(lines);

        // Display frequent itemsets.
        model.freqItemsets().orderBy(functions.col("freq").desc()).show(false);

        // Display generated association rules
        model.associationRules().orderBy(functions.col("confidence").desc()).show(false);

        spark.stop();
    }

}