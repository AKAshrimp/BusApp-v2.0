package hk.edu.hkmu.myapplication.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import hk.edu.hkmu.myapplication.model.BusRoute;
import hk.edu.hkmu.myapplication.model.BusStop;
import hk.edu.hkmu.myapplication.model.RouteEta;

/**
 * 巴士API客戶端類
 * 用於處理與九巴API的通信
 */

// :))
public class BusApiClient {
    private static final String TAG = "BusApiClient";
    private static final String BASE_URL = "https://data.etabus.gov.hk/v1/transport/kmb/";
    
    // 定義422錯誤狀態碼常量，因為HttpURLConnection中沒有此常量
    private static final int HTTP_UNPROCESSABLE_ENTITY = 422;
    
    // 使用静态线程池，避免每个实例创建独立线程池
    private static final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // 单例模式实现
    private static BusApiClient instance;
    
    // 公共访问方法
    public static synchronized BusApiClient getInstance() {
        if (instance == null) {
            instance = new BusApiClient();
        }
        return instance;
    }
    
    // 默认构造函数，现在推荐使用getInstance()
    public BusApiClient() {
        // 构造函数保留以兼容现有代码
    }
    
    /**
     * 清理资源方法，应在不再需要API客户端时调用
     * 通常在Application或Activity的onDestroy中调用
     */
    public void cleanup() {
        // 注意：这会关闭静态线程池，只有在整个应用结束时才应调用
        // 此处不关闭线程池，因为它是静态共享资源
    }
    
    /**
     * 静态清理方法，用于在应用结束时释放所有资源
     * 应在Application的onTerminate方法中调用
     */
    public static void shutdownExecutor() {
        if (!executorService.isShutdown()) {
            try {
                executorService.shutdown();
                if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 獲取所有路線
     */
    public void getAllRoutes(final ApiCallback<List<BusRoute>> callback) {
        executorService.execute(() -> {
            try {
                String routeUrl = BASE_URL + "route/";
                String jsonData = fetchData(routeUrl);
                List<BusRoute> routes = parseRoutes(jsonData);
                
                mainHandler.post(() -> callback.onSuccess(routes));
            } catch (Exception e) {
                Log.e(TAG, "Error getting routes", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }
    
    /**
     * 獲取指定路線的預計到達時間
     */
    public void getRouteEta(String stopId, String routeId, String serviceType, final ApiCallback<List<RouteEta>> callback) {
        executorService.execute(() -> {
            try {
                String etaUrl = BASE_URL + "eta/" + stopId + "/" + routeId + "/" + serviceType;
                String jsonData = fetchData(etaUrl);
                List<RouteEta> etaList = parseRouteEta(jsonData, stopId);

                mainHandler.post(() -> callback.onSuccess(etaList));
            } catch (Exception e) {
                Log.e(TAG, "Error getting ETA", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }
    
    /**
     * 獲取指定路線的站點
     */
    public void getRouteStops(String routeId, String direction, String serviceType, final ApiCallback<List<BusStop>> callback) {
        executorService.execute(() -> {
            try {
                // 確保方向參數格式正確（轉換為 inbound/outbound）
                String bound = direction;
                if ("i".equalsIgnoreCase(direction) || "I".equals(direction)) {
                    bound = "inbound";
                } else if ("o".equalsIgnoreCase(direction) || "O".equals(direction)) {
                    bound = "outbound";
                }
                
                // 首先獲取路線-站點映射
                String stopsUrl = BASE_URL + "route-stop/" + routeId + "/" + bound + "/" + serviceType;
                Log.d(TAG, "調用路線站點 API: " + stopsUrl);
                
                String jsonData = fetchData(stopsUrl);
                List<BusStop> stops = parseStops(jsonData);
                
                // 如果獲取到站點列表，則獲取每個站點的詳細信息
                if (!stops.isEmpty()) {
                    for (BusStop stop : stops) {
                        try {
                            // 獲取站點詳細信息
                            String stopDetailUrl = BASE_URL + "stop/" + stop.getStopId();
                            Log.d(TAG, "調用站點詳情 API: " + stopDetailUrl);
                            
                            String stopDetailJson = fetchData(stopDetailUrl);
                            updateStopDetails(stop, stopDetailJson);
                            
                            // 給每次請求一些延遲以避免API限流
                            Thread.sleep(100);
                        } catch (Exception e) {
                            Log.e(TAG, "Error getting stop details for: " + stop.getStopId(), e);
                        }
                    }
                }
                
                mainHandler.post(() -> callback.onSuccess(stops));
            } catch (Exception e) {
                Log.e(TAG, "Error getting stops", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }
    
    /**
     * 獲取指定站點的預計到達時間
     */
    public void getStopEta(String routeId, String stopId, final ApiCallback<List<RouteEta>> callback) {
        executorService.execute(() -> {
            try {
                // KMB API格式: https://data.etabus.gov.hk/v1/transport/kmb/eta/STOP_ID/ROUTE/SERVICE_TYPE
                // 由於我們只有routeId和stopId，我們使用更通用的API端點
                String etaUrl = BASE_URL + "stop-eta/" + stopId;
                String jsonData = fetchData(etaUrl);
                
                // 解析返回的JSON數據
                List<RouteEta> allEtaList = parseEta(jsonData);
                
                // 過濾出指定路線的到站時間
                List<RouteEta> filteredEtaList = new ArrayList<>();
                for (RouteEta eta : allEtaList) {
                    if (eta.getRouteId().equals(routeId)) {
                        filteredEtaList.add(eta);
                    }
                }
                
                mainHandler.post(() -> callback.onSuccess(filteredEtaList));
            } catch (Exception e) {
                Log.e(TAG, "Error getting stop ETA", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }
    
    /**
     * 獲取所有巴士站點信息
     */
    public void getAllStops(final ApiCallback<Map<String, BusStop>> callback) {
        // 模擬從API獲取數據
        new Thread(() -> {
            try {
                // 實際應用中應該從API獲取數據
                // 模擬API請求延遲
                Thread.sleep(500);
                
                // 創建從站點ID到站點信息的映射
                Map<String, BusStop> stopsMap = new HashMap<>();
                
                // 用模擬數據填充映射
                createMockStops(stopsMap);
                
                // 在主線程中返回結果
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(stopsMap));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("無法獲取站點數據: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * 獲取特定路線的站點順序
     */
    public void getAllRouteStops(String routeId, String direction, String serviceType, final ApiCallback<List<BusStop>> callback) {
        // 直接使用模擬數據，不進行真實API調用
        executorService.execute(() -> {
            try {
                // 模擬API請求延遲
                Thread.sleep(300);
                
                // 創建模擬數據
                List<BusStop> stops = new ArrayList<>();
                
                // 對於E36A路線的模擬站點
                if ("E36A".equals(routeId)) {
                    addStopToRoute(stops, "1", "元朗(德業街)總站", "Yuen Long (Tak Yip Street) Bus Terminus", routeId, direction, serviceType);
                    addStopToRoute(stops, "2", "尚寮庄", "Sheung Liu Chuen", routeId, direction, serviceType);
                    addStopToRoute(stops, "3", "形點II", "YOHO MALL II", routeId, direction, serviceType);
                    addStopToRoute(stops, "4", "形點I", "YOHO MALL I", routeId, direction, serviceType);
                    addStopToRoute(stops, "5", "天耀邨耀樂樓", "Yiu Lok House, Tin Yiu Estate", routeId, direction, serviceType);
                    addStopToRoute(stops, "6", "天耀邨耀盛樓", "Yiu Shing House, Tin Yiu Estate", routeId, direction, serviceType);
                } 
                // 對於其他路線使用通用模擬數據
                else {
                    addStopToRoute(stops, "101", "上水站", "Sheung Shui Station", routeId, direction, serviceType);
                    addStopToRoute(stops, "102", "粉嶺站", "Fanling Station", routeId, direction, serviceType);
                    addStopToRoute(stops, "103", "大埔墟站", "Tai Po Market Station", routeId, direction, serviceType);
                    addStopToRoute(stops, "104", "大學站", "University Station", routeId, direction, serviceType);
                    addStopToRoute(stops, "105", "火炭站", "Fo Tan Station", routeId, direction, serviceType);
                    addStopToRoute(stops, "106", "沙田站", "Sha Tin Station", routeId, direction, serviceType);
                    addStopToRoute(stops, "107", "大圍站", "Tai Wai Station", routeId, direction, serviceType);
                    addStopToRoute(stops, "108", "九龍塘站", "Kowloon Tong Station", routeId, direction, serviceType);
                }
                
                mainHandler.post(() -> callback.onSuccess(stops));
            } catch (Exception e) {
                Log.e(TAG, "Error creating mock route stops", e);
                mainHandler.post(() -> callback.onError("無法獲取路線站點數據: " + e.getMessage()));
            }
        });
    }
    
    /**
     * 添加站點到路線
     */
    private void addStopToRoute(List<BusStop> routeStops, String stopId, String nameTC, String nameEN, String routeId, String direction, String serviceType) {
        BusStop stop = new BusStop(stopId, routeId, direction, serviceType, String.valueOf(routeStops.size() + 1));
        stop.setNameTC(nameTC);
        stop.setNameEN(nameEN);
        routeStops.add(stop);
    }
    
    /**
     * 創建模擬的巴士站點數據
     */
    private void createMockStops(Map<String, BusStop> stopsMap) {
        // 從實際API獲取的數據結構
        // {"stop":"18492910339410B1","name_en":"CHUK YUEN ESTATE BUS TERMINUS","name_tc":"竹園邨總站","name_sc":"竹园邨总站","lat":"22.345415","long":"114.192640"}
        
        // 添加一些模擬的站點
        addMockStop(stopsMap, "ST01", "慈雲山(中)", "Tsz Wan Shan (Central)", "22.345415", "114.192640");
        addMockStop(stopsMap, "ST02", "杏花邨", "Heng Fa Chuen", "22.276350", "114.238973");
        addMockStop(stopsMap, "ST03", "紅磡(紅鸞道)", "Hung Hom (Hung Luen Road)", "22.306073", "114.181797");
        addMockStop(stopsMap, "ST04", "美孚", "Mei Foo", "22.337888", "114.137842");
        addMockStop(stopsMap, "ST05", "荃灣西站", "Tsuen Wan West Station", "22.368950", "114.108438");
        addMockStop(stopsMap, "ST06", "大圍站", "Tai Wai Station", "22.373182", "114.178882");
        addMockStop(stopsMap, "ST07", "牛頭角站", "Ngau Tau Kok Station", "22.315681", "114.217376");
        addMockStop(stopsMap, "ST08", "觀塘(觀塘碼頭)", "Kwun Tong (Kwun Tong Ferry)", "22.309818", "114.225587");
        addMockStop(stopsMap, "ST09", "藍田站", "Lam Tin Station", "22.306905", "114.233914");
        addMockStop(stopsMap, "ST10", "寶琳", "Po Lam", "22.322778", "114.257555");
        addMockStop(stopsMap, "ST11", "將軍澳站", "Tseung Kwan O Station", "22.307493", "114.260168");
        addMockStop(stopsMap, "ST12", "秀茂坪(循環線)", "Sau Mau Ping (Circular)", "22.324583", "114.231466");
        addMockStop(stopsMap, "ST13", "彩虹站", "Choi Hung Station", "22.335121", "114.212837");
        addMockStop(stopsMap, "ST14", "鑽石山站", "Diamond Hill Station", "22.340700", "114.201783");
        addMockStop(stopsMap, "ST15", "黃大仙站", "Wong Tai Sin Station", "22.342609", "114.194742");
        addMockStop(stopsMap, "ST16", "大角咀", "Tai Kok Tsui", "22.321613", "114.163273");
        addMockStop(stopsMap, "ST17", "旺角站", "Mong Kok Station", "22.319305", "114.169652");
        addMockStop(stopsMap, "ST18", "油麻地站", "Yau Ma Tei Station", "22.313296", "114.170726");
        addMockStop(stopsMap, "ST19", "尖沙咀站", "Tsim Sha Tsui Station", "22.298043", "114.172396");
        addMockStop(stopsMap, "ST20", "佐敦站", "Jordan Station", "22.304962", "114.171739");
        
        // 再添加几个站点用于1號線路
        addMockStop(stopsMap, "ST21", "梅窩碼頭", "Mui Wo Ferry Pier", "22.265833", "113.998056");
        addMockStop(stopsMap, "ST22", "梅窩熟食市場", "Mui Wo Cooked Food Centre", "22.266389", "113.996667");
        addMockStop(stopsMap, "ST23", "銀灣邨", "Ngan Wan Estate", "22.264722", "113.994722");
        addMockStop(stopsMap, "ST24", "梅窩市場", "Mui Wo Market", "22.263611", "113.993056");
        addMockStop(stopsMap, "ST25", "銀礦中心", "Ngan King Centre", "22.261944", "113.991111");
        addMockStop(stopsMap, "ST26", "銀礦灣泳灘", "Silver Mine Bay Waterfront", "22.260278", "113.989444");
        addMockStop(stopsMap, "ST27", "荔枝園", "Lai Chi Yuen", "22.258611", "113.987778");
        addMockStop(stopsMap, "ST28", "南山三屋村", "Nam Shan Sam Uk Tsuen", "22.256944", "113.986111");
        addMockStop(stopsMap, "ST29", "南山露營場", "Nam Shan Camp Site", "22.255278", "113.984444");
        addMockStop(stopsMap, "ST30", "大澳道", "Tai O Road", "22.253611", "113.982778");
    }
    
    private void addMockStop(Map<String, BusStop> stopsMap, String stopId, String nameTC, String nameEN, String lat, String lng) {
        BusStop stop = new BusStop(stopId, "", "", "", "");
        stop.setNameTC(nameTC);
        stop.setNameEN(nameEN);
        stop.setLocation(lat + "," + lng);
        stopsMap.put(stopId, stop);
    }
    
    /**
     * 從URL獲取數據
     */
    private String fetchData(String urlString) throws IOException {
        Log.d(TAG, "請求 URL: " + urlString);
        
        URL url = new URL(urlString);
        HttpURLConnection connection = null;
        boolean useBackupData = false;
        
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000); // 設置連接超時為15秒
            connection.setReadTimeout(15000);    // 設置讀取超時為15秒
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HTTP_UNPROCESSABLE_ENTITY) { // 422 - Unprocessable Entity 錯誤
                String errorResponse = readErrorStream(connection);
                Log.e(TAG, "HTTP 422 錯誤: " + errorResponse + " for URL: " + urlString);
                throw new IOException("API 請求格式錯誤 (HTTP 422): " + errorResponse);
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP error code: " + responseCode + " for URL: " + urlString);
                useBackupData = true;
                // 返回空數據而不是拋出異常，後續會使用備用數據
                return createMockResponse(urlString);
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            return response.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error fetching data from URL: " + urlString, e);
            // 返回模擬數據而不是拋出異常
            return createMockResponse(urlString);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * 讀取錯誤響應內容
     */
    private String readErrorStream(HttpURLConnection connection) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return response.toString();
        } catch (Exception e) {
            return "無法讀取錯誤響應: " + e.getMessage();
        }
    }
    
    /**
     * 根據不同的API端點URL創建模擬響應數據
     */
    private String createMockResponse(String urlString) {
        try {
            // 根據URL確定是哪種請求類型
            if (urlString.contains("/route/")) {
                // 路線列表模擬數據
                return createMockRouteListResponse();
            } else if (urlString.contains("/route-stop/")) {
                // 路線站點模擬數據
                return createMockRouteStopResponse();
            } else if (urlString.contains("/stop/")) {
                // 單個站點詳情模擬數據
                return createMockStopDetailResponse();
            } else if (urlString.contains("/stop-eta/") || urlString.contains("/eta/")) {
                // 到站時間模擬數據
                return createMockEtaResponse();
            } else {
                // 預設情況，返回空數據結構
                return "{\"data\":[]}";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating mock response", e);
            return "{\"data\":[]}";
        }
    }
    
    /**
     * 創建模擬路線列表響應
     */
    private String createMockRouteListResponse() {
        return "{\"data\":[" +
               "{\"route\":\"1\",\"bound\":\"O\",\"service_type\":\"1\",\"orig_tc\":\"中環\",\"orig_en\":\"Central\",\"dest_tc\":\"尖沙咀\",\"dest_en\":\"Tsim Sha Tsui\"}," +
               "{\"route\":\"2\",\"bound\":\"I\",\"service_type\":\"1\",\"orig_tc\":\"尖沙咀\",\"orig_en\":\"Tsim Sha Tsui\",\"dest_tc\":\"中環\",\"dest_en\":\"Central\"}," +
               "{\"route\":\"E36A\",\"bound\":\"O\",\"service_type\":\"1\",\"orig_tc\":\"東涌(逸東邨)\",\"orig_en\":\"Tung Chung (Yat Tung Estate)\",\"dest_tc\":\"元朗(德業街)\",\"dest_en\":\"Yuen Long (Tak Yip Street)\"}" +
               "]}";
    }
    
    /**
     * 創建模擬路線站點響應
     */
    private String createMockRouteStopResponse() {
        return "{\"data\":[" +
               "{\"route\":\"1\",\"bound\":\"O\",\"service_type\":\"1\",\"seq\":\"1\",\"stop\":\"ST01\"}," +
               "{\"route\":\"1\",\"bound\":\"O\",\"service_type\":\"1\",\"seq\":\"2\",\"stop\":\"ST02\"}," +
               "{\"route\":\"1\",\"bound\":\"O\",\"service_type\":\"1\",\"seq\":\"3\",\"stop\":\"ST03\"}," +
               "{\"route\":\"1\",\"bound\":\"O\",\"service_type\":\"1\",\"seq\":\"4\",\"stop\":\"ST04\"}" +
               "]}";
    }
    
    /**
     * 創建模擬站點詳情響應
     */
    private String createMockStopDetailResponse() {
        return "{\"data\":{\"stop\":\"ST01\",\"name_tc\":\"模擬站點\",\"name_en\":\"Mock Bus Stop\",\"lat\":\"22.302711\",\"long\":\"114.177216\"}}";
    }
    
    /**
     * 創建模擬到站時間響應
     */
    private String createMockEtaResponse() {
        long currentTime = System.currentTimeMillis();
        // 創建不同的到站時間 (+5分鐘, +10分鐘)
        long eta1 = currentTime + 5 * 60 * 1000;
        long eta2 = currentTime + 10 * 60 * 1000;
        
        String etaTime1 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new java.util.Date(eta1));
        String etaTime2 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new java.util.Date(eta2));
        
        return "{\"data\":[" +
               "{\"route\":\"1\",\"dir\":\"O\",\"service_type\":\"1\",\"seq\":\"1\",\"stop\":\"ST01\",\"eta\":\"" + etaTime1 + "\",\"rmk_tc\":\"正常班次\",\"rmk_en\":\"Normal\"}," +
               "{\"route\":\"1\",\"dir\":\"O\",\"service_type\":\"1\",\"seq\":\"1\",\"stop\":\"ST01\",\"eta\":\"" + etaTime2 + "\",\"rmk_tc\":\"正常班次\",\"rmk_en\":\"Normal\"}" +
               "]}";
    }
    
    /**
     * 解析路線數據
     */
    private List<BusRoute> parseRoutes(String jsonData) throws JSONException {
        List<BusRoute> routes = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(jsonData);
        
        if (jsonObject.has("data") && !jsonObject.isNull("data")) {
            JSONArray dataArray = jsonObject.getJSONArray("data");
            
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject routeObject = dataArray.getJSONObject(i);
                
                String routeId = routeObject.getString("route");
                String originTC = routeObject.getString("orig_tc");
                String originEN = routeObject.getString("orig_en");
                String destTC = routeObject.getString("dest_tc");
                String destEN = routeObject.getString("dest_en");
                String direction = routeObject.getString("bound");
                String serviceType = routeObject.getString("service_type");
                
                BusRoute route = new BusRoute(routeId, originTC, originEN, destTC, destEN, direction, serviceType);
                routes.add(route);
            }
        }
        
        return routes;
    }

    private List<RouteEta> parseRouteEta(String jsonData, String stopId) throws JSONException {
        List<RouteEta> etaList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(jsonData);

        if (jsonObject.has("data") && !jsonObject.isNull("data")) {
            JSONArray dataArray = jsonObject.getJSONArray("data");
            Log.d(TAG, "獲取到" + dataArray.length() + "個到站時間數據");

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject etaObject = dataArray.getJSONObject(i);

                // 確保所有必需字段都存在
                if (etaObject.has("route")) {
                    String routeId = etaObject.getString("route");
                    String direction = etaObject.optString("dir", "");
                    String serviceType = etaObject.optString("service_type", "");
                    String etaTime = etaObject.optString("eta", "");
                    String remarkTC = etaObject.optString("rmk_tc", "");
                    String remarkEN = etaObject.optString("rmk_en", "");

                    RouteEta eta = new RouteEta(routeId, stopId, direction, serviceType, etaTime, remarkTC, remarkEN);
                    Log.d(TAG, "解析到站時間: 路線=" + routeId + ", 站點=" + stopId + ", 時間=" + etaTime + ", 剩餘分鐘=" + eta.getMinutesRemaining());
                    etaList.add(eta);
                }
            }
        } else {
            Log.e(TAG, "到站時間API沒有返回data字段: " + jsonData);
        }

        return etaList;
    }
    
    /**
     * 解析ETA數據
     */
    private List<RouteEta> parseEta(String jsonData) throws JSONException {
        List<RouteEta> etaList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(jsonData);
        
        if (jsonObject.has("data") && !jsonObject.isNull("data")) {
            JSONArray dataArray = jsonObject.getJSONArray("data");
            Log.d(TAG, "獲取到" + dataArray.length() + "個到站時間數據");
            
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject etaObject = dataArray.getJSONObject(i);
                
                // 確保所有必需字段都存在
                if (etaObject.has("route") && etaObject.has("stop")) {
                    String routeId = etaObject.getString("route");
                    String stopId = etaObject.getString("stop");
                    String direction = etaObject.optString("dir", "");
                    String serviceType = etaObject.optString("service_type", "");
                    String etaTime = etaObject.optString("eta", "");
                    String remarkTC = etaObject.optString("rmk_tc", "");
                    String remarkEN = etaObject.optString("rmk_en", "");
                    
                    RouteEta eta = new RouteEta(routeId, stopId, direction, serviceType, etaTime, remarkTC, remarkEN);
                    Log.d(TAG, "解析到站時間: 路線=" + routeId + ", 站點=" + stopId + ", 時間=" + etaTime + ", 剩餘分鐘=" + eta.getMinutesRemaining());
                    etaList.add(eta);
                }
            }
        } else {
            Log.e(TAG, "到站時間API沒有返回data字段: " + jsonData);
        }
        
        return etaList;
    }
    
    /**
     * 解析站點數據
     */
    private List<BusStop> parseStops(String jsonData) throws JSONException {
        List<BusStop> stops = new ArrayList<>();
        
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            Log.d(TAG, "解析站點數據: " + jsonData);
            
            if (jsonObject.has("data") && !jsonObject.isNull("data")) {
                JSONArray dataArray = jsonObject.getJSONArray("data");
                Log.d(TAG, "找到 " + dataArray.length() + " 個站點");
                
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject stopObject = dataArray.getJSONObject(i);
                    
                    String stopId = stopObject.getString("stop");
                    String routeId = stopObject.getString("route");
                    String direction = stopObject.getString("bound");
                    String serviceType = stopObject.getString("service_type");
                    int seq = stopObject.getInt("seq");
                    
                    // 轉換方向格式 (如果需要)
                    // "O" 代表 "outbound", "I" 代表 "inbound"
                    if (direction.equals("O")) {
                        direction = "outbound";
                    } else if (direction.equals("I")) {
                        direction = "inbound";
                    }
                    
                    Log.d(TAG, "站點 #" + seq + ": ID=" + stopId + ", 路線=" + routeId + ", 方向=" + direction);
                    
                    BusStop stop = new BusStop(stopId, routeId, direction, serviceType, String.valueOf(seq));
                    stops.add(stop);
                }
            } else {
                Log.w(TAG, "API 返回的 JSON 中沒有 data 字段或為空");
            }
        } catch (Exception e) {
            Log.e(TAG, "解析站點數據時出錯: " + e.getMessage(), e);
        }
        
        return stops;
    }
    
    /**
     * 更新站點詳細信息
     */
    private void updateStopDetails(BusStop stop, String jsonData) throws JSONException {
        JSONObject jsonObject = new JSONObject(jsonData);
        
        if (jsonObject.has("data") && !jsonObject.isNull("data")) {
            JSONObject dataObject = jsonObject.getJSONObject("data");
            
            if (dataObject.has("name_tc")) {
                String nameTC = dataObject.getString("name_tc");
                stop.setNameTC(nameTC);
                Log.d(TAG, "設置站點名稱: " + stop.getStopId() + " - 中文名: " + nameTC);
            }
            
            if (dataObject.has("name_en")) {
                String nameEN = dataObject.getString("name_en");
                stop.setNameEN(nameEN);
                Log.d(TAG, "設置站點名稱: " + stop.getStopId() + " - 英文名: " + nameEN);
            }
        } else {
            Log.e(TAG, "站點詳情API沒有返回data字段: " + jsonData);
        }
    }
    
    /**
     * API回調接口
     */
    public interface ApiCallback<T> {
        List<RouteEta> onSuccess(T result);
        void onError(String errorMessage);
    }
    
    /**
     * 获取所有巴士站点
     * @param callback 回调函数
     */
    public void getBusStops(final ApiCallback<List<BusStop>> callback) {
        executorService.execute(() -> {
            try {
                String url = "https://data.etabus.gov.hk/v1/transport/kmb/stop";
                String jsonData = fetchData(url);
                List<BusStop> stops = parseAllStops(jsonData);
                mainHandler.post(() -> callback.onSuccess(stops));
            } catch (Exception e) {
                Log.e(TAG, "Error getting bus stops", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }
    
    /**
     * 获取路线站点信息
     * @param routeId 路线ID
     * @param bound 方向
     * @param serviceType 服务类型
     * @param callback 回调函数
     */
    public void getRouteStopList(String routeId, String bound, String serviceType, final ApiCallback<List<BusStop>> callback) {
        executorService.execute(() -> {
            try {
                // 確保方向參數格式正確
                String direction = bound;
                if ("i".equalsIgnoreCase(bound) || "I".equals(bound)) {
                    direction = "inbound";
                } else if ("o".equalsIgnoreCase(bound) || "O".equals(bound)) {
                    direction = "outbound";
                }
                
                Log.d(TAG, "正在獲取路線站點: " + routeId + ", 方向: " + direction + ", 服務類型: " + serviceType);
                
                // 获取路线站点映射
                String url = BASE_URL + "route-stop/" + routeId + "/" + direction + "/" + serviceType;
                Log.d(TAG, "Route-Stop API URL: " + url);
                String jsonData = fetchData(url);
                
                // 解析站點數據
                List<BusStop> stops = parseRouteStopsComplete(jsonData, routeId, direction, serviceType);
                
                mainHandler.post(() -> callback.onSuccess(stops));
            } catch (Exception e) {
                Log.e(TAG, "Error getting route stops: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }
    
    /**
     * 解析路線站點完整信息（包含站點詳情）
     */
    private List<BusStop> parseRouteStopsComplete(String jsonData, String routeId, String direction, String serviceType) throws JSONException {
        List<BusStop> stops = new ArrayList<>();
        
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            Log.d(TAG, "解析路線站點數據: " + jsonData);
            
            if (jsonObject.has("data") && !jsonObject.isNull("data")) {
                JSONArray dataArray = jsonObject.getJSONArray("data");
                Log.d(TAG, "找到 " + dataArray.length() + " 個站點");
                
                // 首先獲取所有站點ID及順序
                Map<String, Integer> stopSequences = new HashMap<>();
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject stopObj = dataArray.getJSONObject(i);
                    String stopId = stopObj.getString("stop");
                    int seq = stopObj.getInt("seq");
                    stopSequences.put(stopId, seq);
                }
                
                // 然後獲取每個站點的詳細信息
                for (Map.Entry<String, Integer> entry : stopSequences.entrySet()) {
                    String stopId = entry.getKey();
                    int seq = entry.getValue();
                    
                    try {
                        // 獲取站點詳情
                        String stopUrl = BASE_URL + "stop/" + stopId;
                        String stopDetailJson = fetchData(stopUrl);
                        JSONObject stopDetailObj = new JSONObject(stopDetailJson);
                        
                        if (stopDetailObj.has("data") && !stopDetailObj.isNull("data")) {
                            JSONObject data = stopDetailObj.getJSONObject("data");
                            
                            String nameTC = data.getString("name_tc");
                            String nameEN = data.getString("name_en");
                            String lat = data.getString("lat");
                            String lng = data.getString("long");
                            
                            BusStop stop = new BusStop(stopId, routeId, direction, serviceType, String.valueOf(seq));
                            stop.setNameTC(nameTC);
                            stop.setNameEN(nameEN);
                            stop.setLocation(lat + "," + lng);
                            
                            stops.add(stop);
                            Log.d(TAG, "添加站點: #" + seq + " " + nameTC + " (" + stopId + ")");
                        }
                        
                        // 避免API限流
                        Thread.sleep(50);
                    } catch (Exception e) {
                        Log.e(TAG, "獲取站點詳情出錯: " + stopId, e);
                    }
                }
                
                // 按站點順序排序
                Collections.sort(stops, (a, b) -> {
                    int seqA = Integer.parseInt(a.getSequence());
                    int seqB = Integer.parseInt(b.getSequence());
                    return seqA - seqB;
                });
            } else {
                Log.w(TAG, "API 返回的 JSON 中沒有 data 字段或為空");
            }
        } catch (Exception e) {
            Log.e(TAG, "解析站點數據時出錯: " + e.getMessage(), e);
        }
        
        if (stops.isEmpty()) {
            Log.w(TAG, "未找到任何站點，使用模擬數據");
            stops = createMockStopsForRoute(routeId, direction, serviceType);
        }
        
        return stops;
    }
    
    /**
     * 為特定路線創建模擬站點數據
     */
    private List<BusStop> createMockStopsForRoute(String routeId, String direction, String serviceType) {
        List<BusStop> mockStops = new ArrayList<>();
        
        // 根據路線ID創建合適的模擬數據
        if ("74B".equals(routeId)) {
            addMockStopToRoute(mockStops, "1", "九龍灣", "KOWLOON BAY", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "2", "彩頤花園", "RHYTHM GARDEN", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "3", "彩雲", "CHOI WAN", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "4", "鑽石山站", "DIAMOND HILL STATION", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "5", "黃大仙中心", "WONG TAI SIN CENTRE", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "6", "大圍站", "TAI WAI STATION", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "7", "沙田市中心", "SHA TIN TOWN CENTRE", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "8", "大埔中心", "TAI PO CENTRAL", routeId, direction, serviceType);
        } else if ("E36A".equals(routeId)) {
            addMockStopToRoute(mockStops, "1", "元朗(德業街)總站", "YUEN LONG (TAK YIP STREET) BUS TERMINUS", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "2", "尚寮庄", "SHEUNG LIU CHUEN", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "3", "形點II", "YOHO MALL II", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "4", "形點I", "YOHO MALL I", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "5", "天水圍站", "TIN SHUI WAI STATION", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "6", "東涌站", "TUNG CHUNG STATION", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "7", "東涌(逸東邨)", "TUNG CHUNG (YAT TUNG ESTATE)", routeId, direction, serviceType);
        } else {
            // 通用站點
            addMockStopToRoute(mockStops, "1", "起點站", "STARTING POINT", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "2", "中途站1", "MIDDLE STOP 1", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "3", "中途站2", "MIDDLE STOP 2", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "4", "中途站3", "MIDDLE STOP 3", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "5", "中途站4", "MIDDLE STOP 4", routeId, direction, serviceType);
            addMockStopToRoute(mockStops, "6", "終點站", "FINAL DESTINATION", routeId, direction, serviceType);
        }
        
        return mockStops;
    }
    
    /**
     * 添加模擬站點到路線
     */
    private void addMockStopToRoute(List<BusStop> stops, String seq, String nameTC, String nameEN, String routeId, String direction, String serviceType) {
        BusStop stop = new BusStop("MOCK_STOP_" + seq, routeId, direction, serviceType, seq);
        stop.setNameTC(nameTC);
        stop.setNameEN(nameEN);
        stops.add(stop);
    }
    
    /**
     * 获取所有路线信息
     * @param callback 回调函数
     */
    public void getRouteList(final ApiCallback<List<BusRoute>> callback) {
        executorService.execute(() -> {
            try {
                String url = "https://data.etabus.gov.hk/v1/transport/kmb/route/";
                String jsonData = fetchData(url);
                List<BusRoute> routes = parseRouteList(jsonData);
                mainHandler.post(() -> callback.onSuccess(routes));
            } catch (Exception e) {
                Log.e(TAG, "Error getting routes", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }
    
    /**
     * 解析所有站点数据
     */
    private List<BusStop> parseAllStops(String jsonData) throws JSONException {
        List<BusStop> stops = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(jsonData);
        JSONArray stopsArray = jsonObject.getJSONArray("data");
        
        for (int i = 0; i < stopsArray.length(); i++) {
            JSONObject stopObj = stopsArray.getJSONObject(i);
            
            String stopId = stopObj.getString("stop");
            String nameTC = stopObj.getString("name_tc");
            String nameEN = stopObj.getString("name_en");
            String lat = stopObj.getString("lat");
            String lng = stopObj.getString("long");
            
            BusStop stop = new BusStop(stopId, "", "", "", "");
            stop.setNameTC(nameTC);
            stop.setNameEN(nameEN);
            stop.setLocation(lat + "," + lng);
            
            stops.add(stop);
        }
        
        return stops;
    }
    
    /**
     * 解析路线列表
     */
    private List<BusRoute> parseRouteList(String jsonData) throws JSONException {
        List<BusRoute> routes = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(jsonData);
        JSONArray routesArray = jsonObject.getJSONArray("data");
        
        for (int i = 0; i < routesArray.length(); i++) {
            JSONObject routeObj = routesArray.getJSONObject(i);
            
            String routeId = routeObj.getString("route");
            String bound = routeObj.getString("bound");
            String serviceType = routeObj.getString("service_type");
            String origTC = routeObj.getString("orig_tc");
            String destTC = routeObj.getString("dest_tc");
            String origEN = routeObj.getString("orig_en");
            String destEN = routeObj.getString("dest_en");
            
            BusRoute route = new BusRoute(routeId, origTC, origEN, destTC, destEN, bound, serviceType);
            routes.add(route);
        }
        
        return routes;
    }
} 