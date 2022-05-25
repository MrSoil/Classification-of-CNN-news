import eu.hlavki.text.lemmagen.LemmatizerFactory;
import eu.hlavki.text.lemmagen.api.Lemmatizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WebDocument {
    protected String document_url;

    protected List<String> list_of_words;
    protected int documentSize;

    protected HashMap<String, Double> tf_values;
    protected HashMap<String, Double> vector_space;

    public WebDocument(){

    }

    public WebDocument(String document_url) throws IOException, InterruptedException {
        this.document_url = document_url;
        readFromWeb();
        calculateTfValues();
        lengthNormalization();
    }

    protected void readFromWeb() {
        tf_values = new HashMap<>();
        try {
            Document doc = Jsoup.connect(this.document_url).get();
            Elements paragraphs = doc.getElementsByClass("zn-body__paragraph");

            String[] paragraph_splitted;
            list_of_words = new ArrayList<>();
            int word_count = 0;

            Lemmatizer lemmatizer = LemmatizerFactory.getPrebuilt("mlteast-en");

            for (Element paragraph : paragraphs) {
                paragraph_splitted = paragraph.text().split(" ");
                for (String word : paragraph_splitted){
                    String proper_word = trimNonAlphanumericCharacters(lemmatizer.lemmatize(word.toLowerCase(Locale.ROOT)).toString());
                    if (proper_word != null && !proper_word.equals("")){
                        list_of_words.add(proper_word);
                        word_count += paragraph_splitted.length;
                    }
                }
            }

            list_of_words = list_of_words.parallelStream().filter(label ->  !Main.stopwords.contains(label.trim())).collect(Collectors.toList());
            documentSize = word_count;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void calculateTfValues() {

        for (String each_word : list_of_words){
            if (tf_values.get(each_word) == null){
                tf_values.put(each_word, 1.0);
            }
            else {
                tf_values.put(each_word, tf_values.get(each_word) + 1);
            }
        }
        tf_values.keySet().forEach(each_tf -> {
            tf_values.put(each_tf, Math.log(tf_values.get(each_tf)) + 1);
        });
    }

    protected static String trimNonAlphanumericCharacters(String str) {
        String pattern_expression = "^([^a-z0-9]+.*[a-z0-9]*)$|^([^a-z0-9]+.*[^a-z0-9])$|^([a-z0-9]*.*[^a-z0-9]+)$";
        Pattern pattern = Pattern.compile(pattern_expression);

        Matcher matcher = pattern.matcher(str);

        if (!matcher.find()){
            return str;
        }
        else if (matcher.group(1) != null) {
            str = str.substring(1);
            return trimNonAlphanumericCharacters(str);
        }
        else if (matcher.group(2) != null) {
            str = str.substring(1, str.length() - 1);
            return trimNonAlphanumericCharacters(str);
        }
        else if (matcher.group(3) != null) {
            str = str.substring(0, str.length() - 1);
            return trimNonAlphanumericCharacters(str);
        }
        else {
            return str;
        }
    }

    protected void lengthNormalization() {

        vector_space = new HashMap<String, Double>();
        AtomicReference<Double> divide_by = new AtomicReference<>(0.0);

        tf_values.forEach((key, tf_value) ->
                divide_by.set(divide_by.get() + Math.pow(tf_value, 2)));

        divide_by.set(Math.sqrt(divide_by.get()));


        tf_values.forEach((key, tf_value) ->
        {
            vector_space.put(key, (tf_value / divide_by.get()));
        });

    }

    public String getDocument_url() {
        return document_url;
    }

    public void setDocument_url(String document_url) {
        this.document_url = document_url;
    }

    public List<String> getList_of_words() {
        return list_of_words;
    }

    public void setList_of_words(List<String> list_of_words) {
        this.list_of_words = list_of_words;
    }

    public int getDocumentSize() {
        return documentSize;
    }

    public void setDocumentSize(int documentSize) {
        this.documentSize = documentSize;
    }

    public HashMap<String, Double> getTf_values() {
        return tf_values;
    }

    public void setTf_values(HashMap<String, Double> tf_values) {
        this.tf_values = tf_values;
    }

    public HashMap<String, Double> getVector_space() {
        return vector_space;
    }

    public void setVector_space(HashMap<String, Double> vector_space) {
        this.vector_space = vector_space;
    }
}