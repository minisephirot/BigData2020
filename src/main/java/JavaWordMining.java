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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JavaWordMining {

    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static double minsup = 0.5;
    private static double minconf = 0.5;
    private static int nbline = 20;
    private static String path = "";

    static void printUsage(){
        System.out.println("Usage : java JavaWordMining $1 $2 $3 $4 where $X is :\n " +
                "\t $1 : cf or cp, the ressource folder used, required \n" +
                "\t $2 : number of line to display, if missing = 20. \n" +
                "\t $3 : minsup parameter between [0:1], if missing = 0.5. \n" +
                "\t $4 : minconf parameter between [0:1], if missing = 0.5. \n");
        System.exit(0);
    }

    public static void main(String[] args) throws IOException {

        if (args.length != 1 && args.length != 4 ){
            printUsage();
        }

        if (args[0].equalsIgnoreCase("cf")) path = "src/main/resources/cf";
        if (args[0].equalsIgnoreCase("cp")) path = "src/main/resources/cp";
        if (path.isEmpty()) printUsage();

        if (args.length == 4){
            nbline = Integer.parseInt(args[1]);
            minsup = Double.parseDouble(args[2]);
            minconf = Double.parseDouble(args[3]);
            System.out.println("Going throught "+ path +" folder with [minsup,minconf] = ["+minsup+","+minconf+"].");
        }

        System.setProperty("hadoop.home.dir", "C:\\winutil\\");
        //Démarrage de Spark en Java
        SparkSession spark = SparkSession
                .builder()
                .appName("JavaWordMining")
                .config("spark.master", "local")
                .getOrCreate();

        //Etape 1 : Parsing des .txt et des stopwords en transactions pour travailler dessus, on retirera aussi la ponctuation et les espaces vides
        File c = new File(path);

        //Etape 2: Filtrage des stopwords dans chaques transactions
        String loadsw = new String(Files.readAllBytes(Paths.get("src/main/resources/french-stopwords.txt")));
        //Transformation en tableau de string pour chaque mot
        List<String> stopwords = Arrays.asList(SPACE.split(loadsw));

        //Classe faite pour retirer les stopwords
        StopWordsRemover remover = new StopWordsRemover();
        remover.setStopWords((String[]) stopwords.toArray());
        //Définition de la colonne à modifier
        remover.setInputCol("items");
        //Définition de la colonne crée à la fin
        remover.setOutputCol("new");

        ArrayList<Row> tab = new ArrayList<>();
        for (File f: Objects.requireNonNull(c.listFiles())) {
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

        //Application de FPGrowth
        FPGrowthModel model = new FPGrowth()
                .setItemsCol("new")
                .setMinSupport(minsup)
                .setMinConfidence(minconf)
                .fit(lines);

        //Affichage.
        model.freqItemsets().orderBy(functions.col("freq").desc()).show(nbline,false);
        model.associationRules().orderBy(functions.col("confidence").desc()).show(nbline,false);

        spark.stop();
    }
}
