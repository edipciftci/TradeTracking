import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class App {
    public static void main(String[] args) throws Exception {

        // Creating and sending a request to get the trade history
        HttpRequest request = (HttpRequest) HttpRequest.newBuilder()
        .uri(URI.create("https://seffaflik.epias.com.tr/transparency/service/market/intra-day-trade-history?endDate=2022-01-26&startDate=2022-01-26"))
        .GET()
        .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        
        // Parsing the response to get the history from the body
        JSONParser parser = new JSONParser();
        JSONObject json = (JSONObject) parser.parse(response.body());
        JSONObject body = (JSONObject) json.get("body");
        ArrayList<JSONObject> tradeList = (ArrayList<JSONObject>) body.get("intraDayTradeHistoryList");

        // Filtering the trade history to get only the trades with the conract name starting with "PH"
        ArrayList<JSONObject> filteredTradeList = new ArrayList<JSONObject>();
        for (JSONObject trade : tradeList) {
            if (((String) trade.get("conract")).substring(0,2).equals("PH"))
                {
                    filteredTradeList.add(trade);
            }
        }

        // Creating the result list with 24 empty objects for each hour
        JSONArray resultList = new JSONArray();
        for (int index = 0; index < 24; index++) {
            JsonObject current = new JsonObject();
            String date = "2022.01.26 " + String.format("%02d", index) + ":00";
            current.addProperty("Tarih", date);
            current.addProperty("Toplam İşlem Miktarı (MWh)", 0);
            current.addProperty("Toplam İşlem Tutarı (TL)", 0);
            current.addProperty("Ağırlık Ortalama Fiyat(TL/MWh)", 0);

            resultList.add(current);
        }
        
        // Going through the filtered trade list and updating the result list
        double toplamİslemTutari, toplamİslemMiktari, agirlikliOrtalamaFiyat;
        double price, quantity;
        for (JSONObject trade : filteredTradeList) {
            String hour = ((String) trade.get("conract")).substring(8,10);
            for (int index = 0; index < 24; index++) {
                if (hour.equals(String.format("%02d", index))) {

                    JsonObject current = (JsonObject) resultList.get(index);
                    toplamİslemMiktari = current.get("Toplam İşlem Miktarı (MWh)").getAsDouble();
                    toplamİslemTutari = current.get("Toplam İşlem Tutarı (TL)").getAsDouble();

                    price = Double.parseDouble(trade.get("price").toString());
                    quantity = Double.parseDouble(trade.get("quantity").toString());
                    toplamİslemMiktari += (quantity / 10);
                    toplamİslemTutari += (quantity * price)/10;
                    agirlikliOrtalamaFiyat = toplamİslemTutari / toplamİslemMiktari;

                    toplamİslemMiktari = Math.round(toplamİslemMiktari * 100.0) / 100.0;
                    toplamİslemTutari = Math.round(toplamİslemTutari * 100.0) / 100.0;
                    agirlikliOrtalamaFiyat = Math.round(agirlikliOrtalamaFiyat * 100.0) / 100.0;

                    current.addProperty("Toplam İşlem Miktarı (MWh)", toplamİslemMiktari);
                    current.addProperty("Toplam İşlem Tutarı (TL)", toplamİslemTutari);
                    current.addProperty("Ağırlık Ortalama Fiyat(TL/MWh)", agirlikliOrtalamaFiyat);

                }
            }
        }

        // Converting the result list to json and writing it to a file
        Gson gson = new Gson();
        String jsonResult = gson.toJson(resultList);
        jsonResult = jsonResult.replace("},", "},\n");

        Path path = Paths.get("result.json");
        Files.write(path, jsonResult.getBytes());

    }

}
