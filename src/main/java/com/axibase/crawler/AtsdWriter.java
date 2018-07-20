package com.axibase.crawler;

import com.axibase.crawler.model.FredCategory;
import com.axibase.crawler.model.FredObservation;
import com.axibase.crawler.model.FredSeries;
import com.axibase.tsd.client.DataService;
import com.axibase.tsd.client.HttpClientManager;
import com.axibase.tsd.client.MetaDataService;
import com.axibase.tsd.model.data.command.AddSeriesCommand;
import com.axibase.tsd.model.data.series.Sample;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

class AtsdWriter {
    private static final String COMMANDS_FILE = "commands.txt";
    private static final String ENTITY_NAME = "fred.stlouisfed.org";
    private static final String EMPTY_VALUE = ".";

    private static final MetricMapper fredSeriesMapper =
            new MetricMapper()
                    .setNameField("id")
                    .setLabelField("title")
                    .setDescriptionField("notes")
                    .setUnitsField("units")
                    .addFieldToTagMapping("id", "series_id")
                    .excludeFromTags("title");

    private static final MetricMapper categoryMapper =
            new MetricMapper()
                    .setTagAutoInclude(false)
                    .addFieldToTagMapping("name", "category")
                    .addFieldToTagMapping("id", "category_id");

    private static final MetricMapper parentCategoryMapper =
            new MetricMapper()
                    .setTagAutoInclude(false)
                    .addFieldToTagMapping("name", "parent_category")
                    .addFieldToTagMapping("id", "parent_category_id");

    private DataService dataService;
    private MetaDataService metaDataService;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private BufferedWriter cmdOut;
    private boolean tracing = false;

    AtsdWriter(HttpClientManager httpClientManager) throws IOException {
        if (tracing) {
            cmdOut = new BufferedWriter(new FileWriter(COMMANDS_FILE));
        }

        dateFormat.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        isoFormat.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));

        dataService = new DataService(httpClientManager);
        metaDataService = new MetaDataService(httpClientManager);
    }

    void setTracing(boolean value) {
        tracing = value;
    }

    private void trace(String command) throws IOException {
        if (tracing) {
            cmdOut.write(command);
        }
    }

    void writeFredSeries(FredSeries fredSeries,
                         FredCategory cat, FredCategory parent,
                         List<String> tags, FredObservation[] data) throws ParseException, IOException {
        MetricBuilder metricBuilder = new MetricBuilder();

        fredSeriesMapper.mapIntoBuilder(fredSeries, metricBuilder);
        categoryMapper.mapIntoBuilder(cat, metricBuilder);
        parentCategoryMapper.mapIntoBuilder(parent, metricBuilder);
        metricBuilder.setTag("tags", tags);

        trace(metricBuilder.toCommand());
        trace("\n");
        metaDataService.createOrReplaceMetric(metricBuilder.toMetric());

        AddSeriesCommand addCommand = new AddSeriesCommand(ENTITY_NAME, fredSeries.getId());
        int k = 0;
        for (FredObservation observation : data) {
            long date = dateFormat.parse(observation.getDate()).getTime();
            if (date < 0)
                continue;

            String strValue = observation.getValue();
            double value = Double.NaN;

            boolean text = false;

            Sample observationSample;

            if (!strValue.equals(EMPTY_VALUE)) {
                try {
                    value = Double.parseDouble(strValue);
                } catch (NumberFormatException e) {
                    text = true;
                }
            } else {
                strValue = "NaN";
            }

            if (!text) {
                observationSample = Sample.ofTimeDouble(date, value);
            } else {
                observationSample = new Sample(date, Double.NaN, strValue);
            }

            addCommand.addSeries(observationSample);
            k++;

            String isoStringDate = isoFormat.format(new Date(date));

            String seriesCmd = String.format("series e:%s m:%s=%s d:%s%n",
                    ENTITY_NAME, fredSeries.getId(), strValue, isoStringDate);

            if (text)
                seriesCmd += String.format(" x:%s=%s", fredSeries.getId(), quoteString(strValue));

            trace(seriesCmd);

            if (k == 100) {
                dataService.addSeries(addCommand);
                addCommand = new AddSeriesCommand(ENTITY_NAME, fredSeries.getId());
            }
        }
        if (k > 0) {
            dataService.addSeries(addCommand);
        }
        if (tracing) {
            cmdOut.flush();
        }
    }

    private String quoteString(String s) {
        if (s == null || s.isEmpty())
            return "\"\"";

        boolean needQuote = false;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '=' || c == '"' || Character.isWhitespace(c))
                needQuote = true;
            if (c == '"')
                sb.append("\"\"");
            else
                sb.append(c);
        }
        String result =  sb.toString();
        if (needQuote)
            result = '"' + result + '"';
        return result;
    }
}
