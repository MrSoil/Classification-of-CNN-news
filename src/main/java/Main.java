import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    /**
     * PATHS DECLERATION
     */
    private static final String stopwords_path = "src/main/resources/english_stopwords.txt";
    private static final String trainer_resources_path = "src/main/resources/trainer_resources";
    private static final String compare_resources_path = "src/main/resources/resource_urls.txt";

    private static final String url = "jdbc:postgresql://localhost:5432/Classification-of-CNN-news";
    private static final String user = "postgres"; // postgresql username
    private static final String password = "0000"; // postgresql password
    static Connection connection;

    public static List<String> stopwords;
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static HashMap<String, TrainedTopic> trainedTopicsHashMap = new HashMap<>();
    private static ArrayList<WebDocument> webDocuments = new ArrayList<>();
    private static List<String> relevantTopics = new ArrayList<>();

    public static void loadStopwords() throws IOException {
        stopwords = Files.readAllLines(Paths.get(stopwords_path));
    }

    public static void findTheMostRelevantTopicsForEachWebDocuments(){
        AtomicReference<Double> highest_cosine = new AtomicReference<>(0.0);
        AtomicReference<TrainedTopic> highest_cosine_TrainedTopic = new AtomicReference<>();

        webDocuments.forEach(webDocument -> {
            highest_cosine.set(0.0);
            trainedTopicsHashMap.forEach((key, trainedTopic) -> {
                if (trainedTopic.cosineSimilarity(webDocument) > highest_cosine.get()){
                    highest_cosine.set(trainedTopic.cosineSimilarity(webDocument));
                    highest_cosine_TrainedTopic.set(trainedTopic);
                }
            });
            System.out.printf("The most relevant topic of %s is %s%n", webDocument.document_url, highest_cosine_TrainedTopic.get().getTrain_name());
        });

    }

    public static void fillTrainedTopicsHashMap(String[] trainFiles) {
        List<String> document_urls = new ArrayList<>();
        Arrays.stream(trainFiles).forEach(filename -> {
            try {
                File trainer_resources = new File(trainer_resources_path + "/" + filename);
                FileReader fr = new FileReader(trainer_resources);
                BufferedReader bufferedReader = new BufferedReader(fr);
                String url;

                while ((url = bufferedReader.readLine()) != null) {
                    document_urls.add(url);
                }
                TrainedTopic temp = new TrainedTopic(document_urls, filename.replaceAll("(\\..+)$", ""));
                trainedTopicsHashMap.put(filename, temp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
        });
    }

    public static void fillWebDocuments(){
        try {
            File resources = new File(compare_resources_path);
            FileReader fr = new FileReader(resources);
            BufferedReader bufferedReader = new BufferedReader(fr);
            String url;
            for (int i = 0; i < 4; i++) {
                bufferedReader.readLine();
            }
            while((url=bufferedReader.readLine())!=null)
            {
                WebDocument webDocument = new WebDocument(url);
                webDocuments.add(webDocument);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void connect() {
        try {
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("Connected the database successfully");
        } catch (Exception e){
            System.out.println("Can not connect the database!");
        }
    }

    public static void main(String[] args) {
        connect();

        try {
            loadStopwords();
            File trainer_path = new File(trainer_resources_path);
            String[] trainer_contents = trainer_path.list();
            fillTrainedTopicsHashMap(trainer_contents);
            fillWebDocuments();
            findTheMostRelevantTopicsForEachWebDocuments();
        }
        catch (FileNotFoundException e) {
            logger.error("Please do not relocate the files or directories!");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}