package hk.edu.hkmu.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import hk.edu.hkmu.myapplication.adapter.BusStopAdapter;
import hk.edu.hkmu.myapplication.api.BusApiClient;
import hk.edu.hkmu.myapplication.model.BusRoute;
import hk.edu.hkmu.myapplication.model.BusStop;
import hk.edu.hkmu.myapplication.model.RouteEta;
import hk.edu.hkmu.myapplication.utils.FavoriteManager;
import hk.edu.hkmu.myapplication.utils.FavoriteUtil;

/**
 * 顯示巴士路线的所有站點
 */
public class RouteStopsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private BusStopAdapter stopAdapter;
    private ProgressBar progressBar;
    private TextView noStopsText;
    private SwipeRefreshLayout swipeRefreshLayout;
    private BusApiClient busApiClient;
    private boolean isEnglish = false;
    private FavoriteManager favoriteManager;
    private Menu optionsMenu;
    private boolean isFavorite = false;
    
    // 路线信息
    private String routeId;
    private String direction;
    private String serviceType;
    private String originTC;
    private String destTC;
    private String originEN;
    private String destEN;
    
    /**
     * 启动活动的静态方法
     */
    public static void start(Context context, BusRoute route) {
        try {
            if (route == null) {
                return;
            }
            
            Intent intent = new Intent(context, RouteStopsActivity.class);
            intent.putExtra("routeId", route.getRouteId());
            intent.putExtra("direction", route.getDirection());
            intent.putExtra("serviceType", route.getServiceType());
            intent.putExtra("originTC", route.getOriginTC());
            intent.putExtra("destTC", route.getDestinationTC());
            intent.putExtra("originEN", route.getOriginEN());
            intent.putExtra("destEN", route.getDestinationEN());
            context.startActivity(intent);
        } catch (Exception e) {
            // 錯誤處理
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_stops);
        
        // 初始化API客户端
        busApiClient = new BusApiClient();
        
        // 初始化收藏管理器
        favoriteManager = FavoriteManager.getInstance(this);
        
        // 获取Intent中的数据
        Intent intent = getIntent();
        routeId = intent.getStringExtra("routeId");
        direction = intent.getStringExtra("direction");
        serviceType = intent.getStringExtra("serviceType");
        originTC = intent.getStringExtra("originTC");
        destTC = intent.getStringExtra("destTC");
        originEN = intent.getStringExtra("originEN");
        destEN = intent.getStringExtra("destEN");
        
        // 检查路线是否已收藏
        checkFavoriteStatus();
        
        // 检查当前语言设置
        checkCurrentLanguage();
        
        // 设置工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            
            // 设置标题：路线号 起点-终点
            String destText = isEnglish ? 
                (destEN != null ? destEN : "") : 
                (destTC != null ? destTC : "");
            String originText = isEnglish ? 
                (originEN != null ? originEN : "") : 
                (originTC != null ? originTC : "");
            
            String title = routeId + " " + originText;
            if (destText != null && !destText.isEmpty()) {
                title += " - " + destText;
            }
            getSupportActionBar().setTitle(title);
        }
        
        // 初始化视图
        progressBar = findViewById(R.id.progress_bar);
        noStopsText = findViewById(R.id.tv_no_stops);
        recyclerView = findViewById(R.id.rv_stops);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // 创建適配器
        stopAdapter = new BusStopAdapter();
        recyclerView.setAdapter(stopAdapter);
        
        // 设置下拉刷新
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this::loadRouteStops);
        swipeRefreshLayout.setColorSchemeResources(R.color.bus_red, R.color.bus_blue);
        
        // 隐藏地图视图和广告
        View mapContainer = findViewById(R.id.map_container);
        if (mapContainer != null) {
            mapContainer.setVisibility(View.GONE);
        }
        
        View adContainer = findViewById(R.id.ad_container);
        if (adContainer != null) {
            adContainer.setVisibility(View.GONE);
        }
        
        // 加载路线站点
        loadRouteStops();
    }
    
    /**
     * 检查路线收藏状态
     */
    private void checkFavoriteStatus() {
        try {
            // 使用FavoriteUtil工具类生成一致的唯一ID
            String uniqueId = FavoriteUtil.standardizeRouteId(routeId, direction, serviceType);
            
            // 刷新收藏管理器數據
            favoriteManager.refresh();
            
            // 檢查收藏狀態
            isFavorite = favoriteManager.isFavorite(uniqueId);
            
            // 如果菜单已初始化，更新菜单项图标
            if (optionsMenu != null) {
                updateFavoriteIcon();
            }
        } catch (Exception e) {
            // 錯誤處理
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.route_stops_menu, menu);
        this.optionsMenu = menu;
        updateFavoriteIcon();
        
        // 添加收藏頁面菜單項
        menu.add(Menu.NONE, R.id.menu_favorites, Menu.NONE, "收藏列表")
            .setIcon(R.drawable.ic_list)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            
        return true;
    }
    
    /**
     * 更新收藏图标
     */
    private void updateFavoriteIcon() {
        try {
            if (optionsMenu != null) {
                MenuItem favoriteItem = optionsMenu.findItem(R.id.action_favorite);
                if (favoriteItem != null) {
                    favoriteItem.getIcon().setLevel(isFavorite ? 1 : 0);
                }
            }
        } catch (Exception e) {
            // 錯誤處理
        }
    }
    
    /**
     * 检查当前系统语言设置
     */
    private void checkCurrentLanguage() {
        Locale currentLocale = getResources().getConfiguration().getLocales().get(0);
        isEnglish = !currentLocale.getLanguage().equals(Locale.CHINESE.getLanguage());
        
        // 如果適配器已初始化，通知其更新語言設置
        if (stopAdapter != null) {
            stopAdapter.updateLanguageSetting(isEnglish);
        }
    }
    


    private void loadRouteStops() {
        showLoading(true);
        busApiClient.getRouteStopList(routeId, direction, serviceType, new BusApiClient.ApiCallback<List<BusStop>>() {
            @Override
            public List<RouteEta> onSuccess(List<BusStop> result) {
                showLoading(false);
                if (result.isEmpty()) {
                    showNoStops(true);
                } else {
                    showNoStops(false);
                    List<RouteEta> allEtas = new ArrayList<>();
                    CountDownLatch latch = new CountDownLatch(result.size()); // Create a latch for the number of stops

                    // Load ETA for each stop
                    for (BusStop bus : result) {
                        loadEtaForStop(bus.getStopId(), allEtas, latch);
                    }

                    // Wait for all ETA loads to finish
                    new Thread(() -> {
                        try {
                            latch.await(); // Wait until all ETA requests are done
                            runOnUiThread(() -> stopAdapter.updateData(result, allEtas)); // Update UI on the main thread
                        } catch (InterruptedException e) {
                            Log.e("RouteStopsActivity", "Error waiting for ETA loading", e);
                        }
                    }).start();
                }//bb
                return null;
            }

            @Override
            public void onError(String errorMessage) {
                showLoading(false);
                Toast.makeText(RouteStopsActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadEtaForStop(String stopId, List<RouteEta> allEtas, CountDownLatch latch) {
        busApiClient.getRouteEta(stopId, routeId, serviceType, new BusApiClient.ApiCallback<List<RouteEta>>() {
            @Override
            public List<RouteEta> onSuccess(List<RouteEta> etaList) {
                if (etaList != null && !etaList.isEmpty()) {
                    Log.d("RouteStopsActivity", "Received " + etaList.size() + " ETAs for stopId: " + stopId);
                    allEtas.addAll(etaList);  // Collect all ETA data
                } else {
                    Log.d("RouteStopsActivity", "No ETA data for stopId: " + stopId);
                }
                latch.countDown(); // Signal that this ETA load is complete
                return etaList;
            }

            @Override
            public void onError(String errorMessage) {
                Log.e("RouteStopsActivity", "Error loading ETA for stopId: " + stopId + ", error: " + errorMessage);
                latch.countDown(); // Ensure latch is decremented even on error
            }
        });
    }

    
    /**
     * 創建模拟巴士站點数据作为備用
     */
    private List<BusStop> createMockBusStops() {
        List<BusStop> mockStops = new ArrayList<>();
        
        // 模擬
        if ("1".equals(routeId)) {
            // 模擬下先
            addMockStop(mockStops, "1", "梅窩碼頭", "Mui Wo Ferry Pier");
            addMockStop(mockStops, "2", "梅窩熟食市場", "Mui Wo Cooked Food Market");
            addMockStop(mockStops, "3", "銀灣邨", "Ngan Wan Estate");
            addMockStop(mockStops, "4", "梅窩市場", "Mui Wo Market");
            addMockStop(mockStops, "5", "銀礦中心", "Ngan King Centre");
            addMockStop(mockStops, "6", "銀礦灣泳灘", "Silver Mine Bay Waterfront");
            addMockStop(mockStops, "7", "荔枝園", "Lai Chi Yuen");
            addMockStop(mockStops, "8", "南山三屋村", "Nam Shan Sam Uk Tsuen");
            addMockStop(mockStops, "9", "南山露營場", "Nam Shan Camp Site");
        } else if ("E36A".equals(routeId)) {
            // 假E36a
            addMockStop(mockStops, "1", "元朗(德業街)總站", "Yuen Long (Tak Yip Street) Bus Terminus");
            addMockStop(mockStops, "2", "尚寮庄", "Sheung Liu Chuen");
            addMockStop(mockStops, "3", "形點II", "YOHO MALL II");
            addMockStop(mockStops, "4", "形點I", "YOHO MALL I");
            addMockStop(mockStops, "5", "天耀邨耀樂樓", "Yiu Lok House, Tin Yiu Estate");
            addMockStop(mockStops, "6", "天耀邨耀盛樓", "Yiu Shing House, Tin Yiu Estate");
        } else {
            // 通用
            addMockStop(mockStops, "1", "上水站", "Sheung Shui Station");
            addMockStop(mockStops, "2", "粉嶺站", "Fanling Station");
            addMockStop(mockStops, "3", "大埔墟站", "Tai Po Market Station");
            addMockStop(mockStops, "4", "沙田站", "Sha Tin Station");
            addMockStop(mockStops, "5", "大圍站", "Tai Wai Station");
            addMockStop(mockStops, "6", "九龍塘站", "Kowloon Tong Station");
        }
        
        return mockStops;
    }
    
    /**
     * 添加模拟站点
     */
    private void addMockStop(List<BusStop> stops, String sequence, String nameTC, String nameEN) {
        BusStop stop = new BusStop(sequence, routeId, direction, serviceType, sequence);
        stop.setNameTC(nameTC);
        stop.setNameEN(nameEN);
        stops.add(stop);
    }
    
    /**
     * 顯示或隱藏加載指示器
     */
    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        swipeRefreshLayout.setRefreshing(isLoading);
    }
    
    /**
     * 顯示或隱藏無站點信息提示
     */
    private void showNoStops(boolean show) {
        noStopsText.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // 检查语言设置
        checkCurrentLanguage();
        
        // 刷新收藏状态
        checkFavoriteStatus();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_favorite) {
            toggleFavorite();
            return true;
        } else if (id == R.id.menu_favorites) {
            // 跳轉到收藏頁面
            Intent intent = new Intent(this, FavoritesActivity.class);
            startActivity(intent);
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * 切换收藏状态
     */
    private void toggleFavorite() {
        try {
            // 使用FavoriteUtil工具类生成一致的唯一ID
            String uniqueId = FavoriteUtil.standardizeRouteId(routeId, direction, serviceType);
            
            // 切换收藏状态
            isFavorite = favoriteManager.toggleFavorite(uniqueId);
            
            // 更新UI
            updateFavoriteIcon();
            
            // 顯示提示消息
            String message = isFavorite ? 
                getString(R.string.action_favorite_added) : 
                getString(R.string.action_favorite_removed);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // 错误处理
        }
    }
} 