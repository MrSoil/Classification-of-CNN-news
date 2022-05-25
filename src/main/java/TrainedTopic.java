import eu.hlavki.text.lemmagen.LemmatizerFactory;
import eu.hlavki.text.lemmagen.api.Lemmatizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class TrainedTopic extends WebDocument{
    private List<String> document_urls;
    private String train_name;
    private HashMap<String, Double> db_tf_values;

    public TrainedTopic(){

    }

    public TrainedTopic(List<String> document_urls, String name) {
        super();
        this.train_name = name;
        this.document_urls = document_urls;
        this.readFromWeb();
        if (Main.connection != null) {
            this.syncWithDB();
        }
        this.calculateTfValues();
        super.lengthNormalization();
    }

    @Override
    protected void readFromWeb() {
        tf_values = new HashMap<>();
        list_of_words = new ArrayList<>();

        AtomicInteger word_count = new AtomicInteger();
        Lemmatizer lemmatizer = null;
        try {
            lemmatizer = LemmatizerFactory.getPrebuilt("mlteast-en");
        } catch (IOException e) {
            e.printStackTrace();
        }
        Lemmatizer finalLemmatizer = lemmatizer;
        document_urls.forEach(document_url -> {
            try {
                Document doc = Jsoup.connect(document_url).get();
                Elements paragraphs = doc.getElementsByClass("zn-body__paragraph");
                String[] paragraph_splitted;
                for (Element paragraph : paragraphs) {
                    paragraph_splitted = paragraph.text().split(" ");
                    for (String word : paragraph_splitted){
                        assert finalLemmatizer != null;
                        String proper_word = trimNonAlphanumericCharacters(finalLemmatizer.lemmatize(word.toLowerCase(Locale.ROOT)).toString());
                        if (proper_word != null && !proper_word.equals("")){
                            list_of_words.add(proper_word);
                            word_count.addAndGet(paragraph_splitted.length);
                        }
                    }
                }

                list_of_words = list_of_words.parallelStream().filter(label ->  !Main.stopwords.contains(label.trim())).collect(Collectors.toList());
                documentSize = word_count.get();

            } catch (IOException e) {
                e.printStackTrace();
            }

        });
    }

    @Override
    protected void calculateTfValues() {

        for (String each_word : list_of_words){
            if (tf_values.get(each_word) == null){
                tf_values.put(each_word, 1.0);
            }
            else {
                tf_values.put(each_word, tf_values.get(each_word) + 1);
            }
        }

        // If there is data in the database
        if (Main.connection != null) {
            db_tf_values.forEach((key, value) -> {
                double frequency;
                frequency = value + ((tf_values.get(key) == null) ? 0 : tf_values.get(key));
                tf_values.put(key, frequency);
            });
            syncWithDB(tf_values);
        }


        tf_values.keySet().forEach(each_tf -> {
            tf_values.put(each_tf, Math.log(tf_values.get(each_tf)) + 1);
        });
    }

    public Double cosineSimilarity(WebDocument webDocument){
        AtomicReference<Double> result = new AtomicReference<>(0.0);
        this.vector_space.forEach((key, value) -> {
            if (webDocument.vector_space.get(key) != null){
                result.updateAndGet(v -> v + webDocument.vector_space.get(key) * value);
            }
        });
        return result.get();
    }

    public void syncWithDB() {
        try {
            db_tf_values = new HashMap<String, Double>();

            String sqlCreate = "CREATE TABLE IF NOT EXISTS tbl_" + this.getTrain_name()
                    + "  (word      VARCHAR(100),"
                    + "   frequency     DOUBLE PRECISION)";

            Statement statement = Main.connection.createStatement();
            statement.execute(sqlCreate);

            String get_query = "SELECT * FROM tbl_" + this.getTrain_name();

            ResultSet rs = statement.executeQuery(get_query);

            while (rs.next()) {
                db_tf_values.put(rs.getString("word"), rs.getDouble("frequency"));
            }
        }
        catch (SQLException | NullPointerException e) {
            System.out.println("Database synchronization is failed!");
        }

    }

    public void syncWithDB(HashMap<String, Double> final_tf_values) {
        try {
            String sqlCreate = "CREATE TABLE IF NOT EXISTS tbl_" + this.getTrain_name()
                    + "  (word      VARCHAR(100),"
                    + "   frequency     DOUBLE PRECISION)";

            Statement statement = Main.connection.createStatement();
            statement.execute(sqlCreate);

            final_tf_values.forEach((key, value) -> {
                String get_query = "SELECT * FROM tbl_" + this.getTrain_name()
                        + "\nWHERE word = '" + key.replaceAll("'", "\"") + "'";

                String set_query = "INSERT INTO"
                        + "     tbl_" + this.getTrain_name() + "(word, frequency)"
                        + "\nVALUES"
                        + "\n     ('" + key.replaceAll("'", "\"") + "','" + value + "');";

                String update_query = "UPDATE tbl_" + this.getTrain_name()
                        + "\nSET frequency = " + value
                        + "\nWHERE word = '" + key.replaceAll("'", "\"") + "'";


                try {
                    if (statement.executeQuery(get_query).next()) {
                        statement.execute(update_query);
                    } else {
                        statement.execute(set_query);
                    }
                } catch (SQLException ignored) {}

            });
        }
        catch (SQLException | NullPointerException e) {
            System.out.println("Database synchronization is failed!");
        }
    }

    public List<String> getDocument_urls() {
        return document_urls;
    }

    public void setDocument_urls(List<String> document_urls) {
        this.document_urls = document_urls;
    }

    public String getTrain_name() {
        return train_name;
    }

    public void setTrain_name(String train_name) {
        this.train_name = train_name;
    }
}
