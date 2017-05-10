package com.axibase.crawler;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler {
    private final DocumentBuilderFactory factory;
    private final String LINE_SEPARATOR = System.getProperty("line.separator");

    public Crawler() {
        factory = DocumentBuilderFactory.newInstance();
    }

    public void process() {
        String[] urls = readUrls();
        ArrayList<String> resultSeries = new ArrayList<>();
        int i = 0;
        for (String url : urls) {
            try {
                i++;
                System.out.println(String.format("Processing %s/%s", i, urls.length));
                List<String> result = processUrl(url);
                resultSeries.addAll(result);
            } catch (Exception ex) {
                System.out.println(String.format("Error processing url %1s", url));
                ex.printStackTrace();
            }
        }

        ArrayList<ArrayList<Byte>> chunkedSeries = new ArrayList<>();
        int maxChunkSizeBytes = 750 * 1024;
        chunkedSeries.add(new ArrayList<Byte>(maxChunkSizeBytes));
        for (String series : resultSeries) {
            series = series + LINE_SEPARATOR;
            byte[] seriesBytes = series.getBytes(Charset.forName("UTF-8"));
            ArrayList<Byte> currentChunk = chunkedSeries.get(chunkedSeries.size() - 1);

            if (currentChunk.size() + seriesBytes.length <= maxChunkSizeBytes) {
                for (Byte currentByte : seriesBytes) {
                    currentChunk.add(currentByte);
                }
            } else {
                currentChunk = new ArrayList<>(maxChunkSizeBytes);
                for (Byte currentByte : seriesBytes) {
                    currentChunk.add(currentByte);
                }
                chunkedSeries.add(currentChunk);
            }
        }

        try {
            for (int chunkIndex = 0; chunkIndex < chunkedSeries.size(); chunkIndex++) {
                ArrayList<Byte> chunk = chunkedSeries.get(chunkIndex);
                try (FileWriter fileWriter = new FileWriter(String.format("result\\series_%s.txt", chunkIndex))) {
                    try (BufferedWriter writer = new BufferedWriter(fileWriter)) {
                        for (Byte currentByte : chunk) {
                            writer.write(currentByte);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private String[] readUrls() {
        Properties properties = new Properties();
        try {
            String propFileName = "urls.properties";
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFileName)) {
                if (inputStream != null) {
                    properties.load(inputStream);
                } else {
                    throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return properties.getProperty("urls").split(";");
    }

    private List<String> processUrl(String urlString) throws
            IOException,
            SAXException,
            ParserConfigurationException,
            XPathExpressionException,
            ParseException {
        DocumentBuilder documentBuilder = factory.newDocumentBuilder();
        Document document = documentBuilder.parse(urlString);

        Node dateNode = document.getElementsByTagName("P").item(2);
        String descriprion = dateNode.getTextContent();
        Pattern pattern = Pattern.compile("20\\d{2}");
        Matcher matcher = pattern.matcher(descriprion);
        matcher.find();
        String[] descriprionParts = descriprion.substring(matcher.start() - 20, matcher.end()).split(" ");

        String[] dateParts = {
                descriprionParts[descriprionParts.length - 3],
                descriprionParts[descriprionParts.length - 2],
                descriprionParts[descriprionParts.length - 1]
        };
        DateFormat reportTimeFormatter = DateFormat.getDateInstance(DateFormat.LONG, Locale.US);
        Date reportDate = reportTimeFormatter.parse(String.format("%s %s %s", dateParts[0], dateParts[1], dateParts[2]));
        String seriesDate = getSeriesDate(reportDate);

        NodeList tableNode = document.getElementsByTagName("ROW");
        List<String> series = new ArrayList<>(tableNode.getLength());
        for (int i = 0; i < tableNode.getLength(); i++) {
            Node currentRow = tableNode.item(i);
            NodeList nameNodes = currentRow.getChildNodes();
            List<String> nameParts = new ArrayList<>(3);
            for (int namePartIndex = 0; namePartIndex < nameNodes.getLength(); namePartIndex++) {
                Node namePartNode = nameNodes.item(namePartIndex);
                if (!namePartNode.getNodeName().equals("ENT")) continue;
                String namePart = namePartNode.getTextContent().replaceAll(" |\\.|\\n", "");
                if (namePart == null || namePart.isEmpty()) continue;
                nameParts.add(namePart);
            }

            StringBuilder seriesBuilder = new StringBuilder();
            seriesBuilder.append("series e:us.irs m:us-expatriate-counter=1");
            if (nameParts.size() > 0) {
                seriesBuilder.append(String.format(" t:last_name=\"%s\"", nameParts.get(0)));
            }
            if (nameParts.size() > 1) {
                seriesBuilder.append(String.format(" t:first_name=\"%s\"", nameParts.get(1)));
            }
            if (nameParts.size() > 2) {
                seriesBuilder.append(String.format(" t:middle_name=\"%s\"", nameParts.get(2)));
            }
            seriesBuilder.append(String.format(" d:%s", seriesDate));
            series.add(seriesBuilder.toString());
        }

        return series;
    }

    private static String getSeriesDate(Date reportDate) {
        Calendar reportDateCalendar = Calendar.getInstance(Locale.US);
        reportDateCalendar.setTime(reportDate);
        int year = reportDateCalendar.get(Calendar.YEAR);
        int month = reportDateCalendar.get(Calendar.MONTH) + 1;
        int day = reportDateCalendar.get(Calendar.DAY_OF_MONTH);

        return String.format("%d-%02d-%2sT00:00:00Z", year, month, day);
    }
}
