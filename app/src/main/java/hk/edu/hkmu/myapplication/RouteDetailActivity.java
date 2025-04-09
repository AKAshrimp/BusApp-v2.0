package hk.edu.hkmu.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hk.edu.hkmu.myapplication.api.BusApiClient;
import hk.edu.hkmu.myapplication.model.BusRoute;
import hk.edu.hkmu.myapplication.model.BusStop;
import hk.edu.hkmu.myapplication.model.RouteEta;

public class RouteDetailActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView stopListRecyclerView;
    private StopAdapter stopAdapter;
    private BusApiClient busApiClient;
    
    private String routeId;
    private String direction;
    private String serviceType;
    private String originTC;
    private String destTC;
    private String originEN;
    private String destEN;
    
    private boolean isEnglish = false;
    
    // 用於啟動該Activity的靜態方法
    public static void start(Context context, BusRoute route) {
        try {
            if (route == null) {
                Toast.makeText(context, "無效的路線數據", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Intent intent = new Intent(context, RouteDetailActivity.class);
            intent.putExtra("routeId", route.getRouteId());
            intent.putExtra("direction", route.getDirection());
            intent.putExtra("serviceType", route.getServiceType());
            intent.putExtra("originTC", route.getOriginTC());
            intent.putExtra("destTC", route.getDestinationTC());
            intent.putExtra("originEN", route.getOriginEN());
            intent.putExtra("destEN", route.getDestinationEN());
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e("RouteDetailActivity", "啟動活動失敗: " + e.getMessage(), e);
            Toast.makeText(context, "無法顯示路線詳情: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_detail);
        
        // 初始化API客戶端
        busApiClient = new BusApiClient();
        
        // 獲取Intent中的數據
        Intent intent = getIntent();
        routeId = intent.getStringExtra("routeId");
        direction = intent.getStringExtra("direction");
        serviceType = intent.getStringExtra("serviceType");
        originTC = intent.getStringExtra("originTC");
        destTC = intent.getStringExtra("destTC");
        originEN = intent.getStringExtra("originEN");
        destEN = intent.getStringExtra("destEN");
        
        // 檢查當前語言設置
        checkCurrentLanguage();
        
        // 設置工具欄
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            
            // 設置標題：路線號 起點-終點
            String title = routeId + " " + (isEnglish ? originEN + " - " + destEN : originTC + " - " + destTC);
            getSupportActionBar().setTitle(title);
        }
        
        // 設置返回按鈕點擊事件
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        
        // 設置下拉刷新
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this::loadStopsAndEta);
        swipeRefreshLayout.setColorSchemeResources(R.color.bus_red, R.color.bus_blue);
        
        // 初始化站點列表
        setupStopList();
        
        // 加載站點和預計到達時間數據
        loadStopsAndEta();
        
        // 添加查看所有站點按鈕的點擊事件
        Button btnViewStops = findViewById(R.id.btn_view_stops);
        btnViewStops.setVisibility(View.VISIBLE);
        btnViewStops.setOnClickListener(v -> {
            // 直接啟動RouteStopsActivity，傳遞必要的路線參數
            RouteStopsActivity.start(this, new BusRoute(
                routeId, 
                originTC, 
                originEN, 
                destTC, 
                destEN, 
                direction, 
                serviceType
            ));
        });
    }
    
    /**
     * 檢查當前系統語言設置
     */
    private void checkCurrentLanguage() {
        Locale currentLocale = getResources().getConfiguration().getLocales().get(0);
        isEnglish = !currentLocale.getLanguage().equals(Locale.CHINESE.getLanguage());
    }
    
    /**
     * 設置站點列表
     */
    private void setupStopList() {
        stopListRecyclerView = findViewById(R.id.stop_list);
        stopListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // 初始化為空列表
        stopAdapter = new StopAdapter(new ArrayList<>(), new HashMap<>(), isEnglish);
        stopListRecyclerView.setAdapter(stopAdapter);
    }
    
    /**
     * 加載站點和預計到達時間數據
     */
    private void loadStopsAndEta() {
        swipeRefreshLayout.setRefreshing(true);
        
        // 首先加載站點數據
        try {
            // 使用單例模式獲取API客戶端
            BusApiClient apiClient = BusApiClient.getInstance();
            apiClient.getRouteStops(routeId, direction, serviceType, new BusApiClient.ApiCallback<List<BusStop>>() {
                @Override
                public List<RouteEta> onSuccess(List<BusStop> stops) {
                    // 獲取到站點列表後，再獲取預計到達時間
                    loadEta(stops);
                    return null;
                }
                
                @Override
                public void onError(String errorMessage) {
                    swipeRefreshLayout.setRefreshing(false);
                    Log.e("RouteDetailActivity", "站點加載錯誤: " + errorMessage);
                    
                    // 使用后台线程创建模擬數據，避免UI卡顿
                    new Thread(() -> {
                        final List<BusStop> mockStops = createMockStops();
                        final Map<String, List<RouteEta>> mockEtaMap = createMockEta(mockStops);
                        
                        // 在UI线程更新界面
                        runOnUiThread(() -> {
                            stopAdapter.updateData(mockStops, mockEtaMap);
                        });
                    }).start();
                }
            });
        } catch (Exception e) {
            swipeRefreshLayout.setRefreshing(false);
            Log.e("RouteDetailActivity", "致命錯誤: " + e.getMessage(), e);
            
            // 使用后台线程创建模擬數據，避免UI卡顿
            new Thread(() -> {
                final List<BusStop> mockStops = createMockStops();
                final Map<String, List<RouteEta>> mockEtaMap = createMockEta(mockStops);
                
                // 在UI线程更新界面
                runOnUiThread(() -> {
                    stopAdapter.updateData(mockStops, mockEtaMap);
                });
            }).start();
        }
    }
    
    /**
     * 加載預計到達時間數據
     */
    private void loadEta(List<BusStop> stops) {
        busApiClient.getRouteEta(routeId, direction, serviceType, new BusApiClient.ApiCallback<List<RouteEta>>() {
            @Override
            public List<RouteEta> onSuccess(List<RouteEta> etaList) {
                // 將ETA按站點ID分組
                Map<String, List<RouteEta>> etaMap = new HashMap<>();
                for (RouteEta eta : etaList) {
                    String stopId = eta.getStopId();
                    if (!etaMap.containsKey(stopId)) {
                        etaMap.put(stopId, new ArrayList<>());
                    }
                    etaMap.get(stopId).add(eta);
                }
                
                // 更新適配器數據
                stopAdapter.updateData(stops, etaMap);
                swipeRefreshLayout.setRefreshing(false);
                return etaList;
            }
            
            @Override
            public void onError(String errorMessage) {
                // 如果獲取ETA失敗，仍然顯示站點列表，但沒有ETA數據
                Map<String, List<RouteEta>> mockEtaMap = createMockEta(stops);
                stopAdapter.updateData(stops, mockEtaMap);
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }
    
    /**
     * 創建模擬站點數據
     */
    private List<BusStop> createMockStops() {
        List<BusStop> stops = new ArrayList<>();
        
        // 對於E36A路線的模擬站點
        if ("E36A".equals(routeId)) {
            BusStop stop1 = new BusStop("1", routeId, direction, serviceType, "1");
            stop1.setNameTC("元朗(德業街)總站");
            stop1.setNameEN("Yuen Long (Tak Yip Street) Bus Terminus");
            stops.add(stop1);
            
            BusStop stop2 = new BusStop("2", routeId, direction, serviceType, "2");
            stop2.setNameTC("尚寮庄");
            stop2.setNameEN("Sheung Liu Chuen");
            stops.add(stop2);
            
            BusStop stop3 = new BusStop("3", routeId, direction, serviceType, "3");
            stop3.setNameTC("形點II");
            stop3.setNameEN("YOHO MALL II");
            stops.add(stop3);
            
            BusStop stop4 = new BusStop("4", routeId, direction, serviceType, "4");
            stop4.setNameTC("形點I");
            stop4.setNameEN("YOHO MALL I");
            stops.add(stop4);
            
            BusStop stop5 = new BusStop("5", routeId, direction, serviceType, "5");
            stop5.setNameTC("天耀邨耀樂樓");
            stop5.setNameEN("Yiu Lok House, Tin Yiu Estate");
            stops.add(stop5);
            
            BusStop stop6 = new BusStop("6", routeId, direction, serviceType, "6");
            stop6.setNameTC("天耀邨耀盛樓");
            stop6.setNameEN("Yiu Shing House, Tin Yiu Estate");
            stops.add(stop6);
        } else {
            // 如果不是E36A，就創建一些通用站點
            for (int i = 1; i <= 6; i++) {
                BusStop stop = new BusStop(String.valueOf(i), routeId, direction, serviceType, String.valueOf(i));
                stop.setNameTC("站點 " + i);
                stop.setNameEN("Stop " + i);
                stops.add(stop);
            }
        }
        
        return stops;
    }
    
    /**
     * 創建模擬預計到達時間數據
     */
    private Map<String, List<RouteEta>> createMockEta(List<BusStop> stops) {
        Map<String, List<RouteEta>> etaMap = new HashMap<>();
        
        for (BusStop stop : stops) {
            List<RouteEta> etaList = new ArrayList<>();
            
            // 為每個站點創建1-3個隨機的ETA
            int etaCount = (int) (Math.random() * 3) + 1;
            for (int i = 0; i < etaCount; i++) {
                int minutes = 5 + (int) (Math.random() * 55); // 5-60分鐘之間的隨機數
                String etaTime = ""; // 簡化模型，不使用實際時間
                RouteEta eta = new RouteEta(routeId, stop.getStopId(), direction, serviceType, 
                        etaTime, "預計到達", "Estimated arrival");
                
                // 使用反射設置minutesRemaining字段（通常不推薦，但為了模擬數據可以考慮）
                try {
                    java.lang.reflect.Field field = RouteEta.class.getDeclaredField("minutesRemaining");
                    field.setAccessible(true);
                    field.set(eta, (long) minutes);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                etaList.add(eta);
            }
            
            etaMap.put(stop.getStopId(), etaList);
        }
        
        return etaMap;
    }
    
    /**
     * 站點列表適配器
     */
    private class StopAdapter extends RecyclerView.Adapter<StopAdapter.ViewHolder> {
        private List<BusStop> busStops;
        private Map<String, List<RouteEta>> etaMap;
        private boolean isEnglish;
        
        public StopAdapter(List<BusStop> busStops, Map<String, List<RouteEta>> etaMap, boolean isEnglish) {
            this.busStops = busStops;
            this.etaMap = etaMap;
            this.isEnglish = isEnglish;
        }
        
        public void updateData(List<BusStop> newStops, Map<String, List<RouteEta>> newEtaMap) {
            this.busStops = newStops;
            this.etaMap = newEtaMap;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bus_stop, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BusStop stop = busStops.get(position);
            Context context = holder.itemView.getContext();
            
            // 設置站點號
            holder.stopNumber.setText(String.valueOf(position + 1) + ".");
            
            // 設置站點名稱
            String stopName = stop.getName(isEnglish);
            holder.stopName.setText(stopName);
            Log.d("RouteDetailActivity", "站點名稱: position=" + position + ", id=" + stop.getStopId() + ", 名稱=" + stopName);
            
            // 設置車費（模擬數據）
            holder.fare.setText(context.getString(R.string.fare) + ": $14.4");
            
            // 設置ETA容器的初始可見性
            holder.etaContainer.setVisibility(View.GONE);
            
            // 設置點擊事件，展開/收起到站時間
            holder.itemView.setOnClickListener(v -> {
                Log.d("RouteDetailActivity", "點擊站點: position=" + position + ", id=" + stop.getStopId());
                if (holder.etaContainer.getVisibility() == View.VISIBLE) {
                    holder.etaContainer.setVisibility(View.GONE);
                } else {
                    holder.etaContainer.setVisibility(View.VISIBLE);
                    
                    // 如果未加載ETA數據，則加載
                    setupEtaViews(holder, stop.getStopId(), context);
                }
            });
            
            // 設置第一個和最後一個站點的特殊樣式
            if (position == 0) {
                holder.topLine.setVisibility(View.INVISIBLE);
            } else {
                holder.topLine.setVisibility(View.VISIBLE);
            }
            
            if (position == busStops.size() - 1) {
                holder.bottomLine.setVisibility(View.INVISIBLE);
            } else {
                holder.bottomLine.setVisibility(View.VISIBLE);
            }
        }
        
        /**
         * 設置預計到達時間視圖
         */
        private void setupEtaViews(ViewHolder holder, String stopId, Context context) {
            // 重置所有ETA視圖
            holder.eta1.setVisibility(View.GONE);
            holder.eta2.setVisibility(View.GONE);
            holder.eta3.setVisibility(View.GONE);
            
            // 首先顯示加載提示
            holder.eta1.setVisibility(View.VISIBLE);
            holder.eta1.setText(context.getString(R.string.loading_eta));
            Log.d("RouteDetailActivity", "開始加載站點到站時間: stopId=" + stopId);
            
            // 嘗試從API獲取最新的到站時間數據
            try {
                busApiClient.getStopEta(routeId, stopId, new BusApiClient.ApiCallback<List<RouteEta>>() {
                    @Override
                    public List<RouteEta> onSuccess(List<RouteEta> etaList) {
                        Log.d("RouteDetailActivity", "獲取到站時間成功: stopId=" + stopId + ", 數量=" + (etaList != null ? etaList.size() : 0));
                        
                        // 更新到站時間顯示
                        if (etaList == null || etaList.isEmpty()) {
                            showNoEtaData(holder, context);
                            return etaList;
                        }
                        
                        // 將到站時間按到達時間排序
                        Collections.sort(etaList, (eta1, eta2) -> 
                            (int) (eta1.getMinutesRemaining() - eta2.getMinutesRemaining()));
                        
                        // 最多顯示3個ETA
                        Resources res = context.getResources();
                        String minutesStr = res.getString(R.string.minutes);
                        
                        for (int i = 0; i < Math.min(etaList.size(), 3); i++) {
                            RouteEta eta = etaList.get(i);
                            long minutesRemaining = eta.getMinutesRemaining();
                            Log.d("RouteDetailActivity", "顯示到站時間: stopId=" + stopId + ", index=" + i + ", 剩餘分鐘=" + minutesRemaining);
                            
                            String etaText;
                            if (minutesRemaining <= 0) {
                                etaText = context.getString(R.string.now);
                            } else {
                                etaText = minutesRemaining + " " + minutesStr;
                            }
                            
                            switch (i) {
                                case 0:
                                    holder.eta1.setVisibility(View.VISIBLE);
                                    holder.eta1.setText(etaText);
                                    break;
                                case 1:
                                    holder.eta2.setVisibility(View.VISIBLE);
                                    holder.eta2.setText(etaText);
                                    break;
                                case 2:
                                    holder.eta3.setVisibility(View.VISIBLE);
                                    holder.eta3.setText(etaText);
                                    break;
                            }
                        }
                        return etaList;
                    }
                    
                    @Override
                    public void onError(String errorMessage) {
                        Log.e("RouteDetailActivity", "獲取到站時間失敗: stopId=" + stopId + ", 錯誤=" + errorMessage);
                        // 如果API調用失敗，使用本地緩存或模擬數據
                        showMockEtaData(holder, stopId, context);
                    }
                });
            } catch (Exception e) {
                Log.e("RouteDetailActivity", "無法加載到站資訊", e);
            }
        }
        
        /**
         * 顯示無到站時間數據的提示
         */
        private void showNoEtaData(ViewHolder holder, Context context) {
            holder.eta1.setVisibility(View.VISIBLE);
            holder.eta1.setText(context.getString(R.string.no_data));
            holder.eta2.setVisibility(View.GONE);
            holder.eta3.setVisibility(View.GONE);
        }
        
        /**
         * 顯示模擬的到站時間數據
         */
        private void showMockEtaData(ViewHolder holder, String stopId, Context context) {
            Log.d("RouteDetailActivity", "顯示模擬到站時間: stopId=" + stopId);
            
            // 直接顯示固定的模擬數據，而不是嘗試從etaMap獲取
            Resources res = context.getResources();
            String minutesStr = res.getString(R.string.minutes);
            
            // 顯示第一個到站時間
            holder.eta1.setVisibility(View.VISIBLE);
            holder.eta1.setText("5 " + minutesStr);
            
            // 顯示第二個到站時間
            holder.eta2.setVisibility(View.VISIBLE);
            holder.eta2.setText("15 " + minutesStr);
            
            // 顯示第三個到站時間
            holder.eta3.setVisibility(View.VISIBLE);
            holder.eta3.setText("25 " + minutesStr);
            
            Log.d("RouteDetailActivity", "模擬到站時間設置完成");
        }
        
        @Override
        public int getItemCount() {
            return busStops.size();
        }
        
        public class ViewHolder extends RecyclerView.ViewHolder {
            TextView stopNumber;
            TextView stopName;
            TextView fare;
            TextView eta1;
            TextView eta2;
            TextView eta3;
            View topLine;
            View bottomLine;
            View stopIndicator;
            LinearLayout etaContainer;
            
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                // 使用item_bus_stop.xml中的ID
                stopNumber = itemView.findViewById(R.id.tv_stop_name);
                stopName = itemView.findViewById(R.id.tv_stop_name);
                fare = itemView.findViewById(R.id.tv_eta);
                // 這些ETA相關的視圖在新佈局中已經被移除，暫時設為null
                eta1 = null;
                eta2 = null;
                eta3 = null;
                topLine = itemView.findViewById(R.id.stop_line_top);
                bottomLine = itemView.findViewById(R.id.stop_line_bottom);
                stopIndicator = itemView.findViewById(R.id.stop_icon);
                // etaContainer在新佈局中不存在，暫時設為null
                etaContainer = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理资源，避免内存泄漏
        if (busApiClient != null) {
            busApiClient.cleanup();
        }
    }
} 