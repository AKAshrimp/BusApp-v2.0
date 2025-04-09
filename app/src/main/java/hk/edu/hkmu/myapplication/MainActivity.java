package hk.edu.hkmu.myapplication;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import hk.edu.hkmu.myapplication.api.BusApiClient;
import hk.edu.hkmu.myapplication.model.BusRoute;
import hk.edu.hkmu.myapplication.model.RouteEta;
import hk.edu.hkmu.myapplication.utils.FavoriteManager;
import hk.edu.hkmu.myapplication.adapter.RouteAdapter;

/**
 * 主活動類，實現巴士查詢應用的主界面
 */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    // 添加底部導航標籤常量
    public static final int TAB_HOME = 0;
    public static final int TAB_FAVORITES = 1;
    public static final int TAB_SEARCH = 2;
    
    // 當前選中的標籤
    public static int currentTab = TAB_HOME;
    
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigationView;
    private RecyclerView recyclerView;
    private RouteAdapter routeAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private BusApiClient busApiClient;
    private List<BusRoute> allRoutes = new ArrayList<>();
    private boolean isEnglish = false;
    private FavoriteManager favoriteManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化工具欄
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // 初始化收藏管理器
        favoriteManager = FavoriteManager.getInstance(this);
        
        // 檢查當前語言設置
        checkCurrentLanguage();
        
        // 初始化抽屜式導航
        drawerLayout = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, 
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        
        // 設置導航視圖監聽器
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        
        // 初始化底部導航欄
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            
            if (id == R.id.nav_favorite) {
                Intent intent = new Intent(this, FavoritesActivity.class);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_search) {
                Intent intent = new Intent(this, SearchActivity.class);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_home) {
                // 已經在首頁，不需要做什麼
                return true;
            }
            
            return false;
        });

        // 初始化RecyclerView
        recyclerView = findViewById(R.id.bus_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        
        // 初始化下拉刷新
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this::loadBusRoutes);
        
        // 創建路線適配器
        routeAdapter = new hk.edu.hkmu.myapplication.adapter.RouteAdapter(this);
        recyclerView.setAdapter(routeAdapter);
        
        // 初始化API客戶端 - 使用单例模式
        busApiClient = BusApiClient.getInstance();
        
        // 加載巴士路線
        loadBusRoutes();
    }
    
    /**
     * 檢查當前系統語言設置
     */
    private void checkCurrentLanguage() {
        Locale currentLocale = getResources().getConfiguration().getLocales().get(0);
        isEnglish = !currentLocale.getLanguage().equals(Locale.CHINESE.getLanguage());
    }

    /**
     * 從API加載巴士路線
     */
    private void loadBusRoutes() {
        swipeRefreshLayout.setRefreshing(true);
        
        // 真實API調用
        busApiClient.getAllRoutes(new BusApiClient.ApiCallback<List<BusRoute>>() {
            @Override
            public List<RouteEta> onSuccess(List<BusRoute> result) {
                // 只顯示部分路線作為示例
                List<BusRoute> filteredRoutes = filterRoutes(result);
                routeAdapter.updateData(filteredRoutes);
                swipeRefreshLayout.setRefreshing(false);
                return null;
            }

            @Override
            public void onError(String errorMessage) {
                // 如果API調用失敗，顯示模擬數據
                List<BusRoute> mockRoutes = createMockBusRoutes();
                routeAdapter.updateData(mockRoutes);
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }
    
    /**
     * 過濾路線（示例中只顯示部分路線）
     */
    private List<BusRoute> filterRoutes(List<BusRoute> allRoutes) {
        List<BusRoute> filtered = new ArrayList<>();
        
        // 只選擇一些特定路線作為示例
        for (BusRoute route : allRoutes) {
            String routeId = route.getRouteId();
            if (routeId.equals("74X") || routeId.equals("E36") || 
                routeId.equals("A41") || routeId.equals("N31") || 
                routeId.equals("42C") || routeId.startsWith("E")) {
                
                if (filtered.size() < 10) { // 限制數量
                    filtered.add(route);
                }
            }
        }
        
        // 如果沒有找到任何路線，使用模擬數據
        if (filtered.isEmpty()) {
            return createMockBusRoutes();
        }
        
        return filtered;
    }

    /**
     * 創建模擬巴士路線數據（當API調用失敗時使用）
     */
    private List<BusRoute> createMockBusRoutes() {
        List<BusRoute> routes = new ArrayList<>();
        
        routes.add(new BusRoute("E36A", "東涌(逸東)", "元朗(德業街)", "outbound", 52));
        routes.add(new BusRoute("E36A", "元朗(德業街)", "東涌(逸東)", "inbound", 36));
        routes.add(new BusRoute("E31", "東涌(逸東)", "荃灣(愉景新城)", "outbound", 15));
        routes.add(new BusRoute("E31", "荃灣(愉景新城)", "東涌(逸東)", "inbound", 10));
        routes.add(new BusRoute("N31", "機場(地面運輸中心)", "東涌(逸東)", "inbound", 3));
        routes.add(new BusRoute("N31", "東涌(逸東)", "機場(地面運輸中心)", "outbound", 6));
        routes.add(new BusRoute("E42P", "東涌(逸東)", "火炭(山尾街)", "outbound", 25));
        routes.add(new BusRoute("N64", "東涌(逸東)", "沙田(廣場)", "outbound", 18));
        
        return routes;
    }

    /**
     * 處理側邊抽屜菜單和底部導航欄項目點擊事件
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.nav_language) {
            switchLanguage();
        } else if (id == R.id.nav_refresh) {
            loadBusRoutes();
        } else if (id == R.id.nav_notification) {
            // 暫時不實現
        } else if (id == R.id.nav_terms) {
            // 暫時不實現
        } else if (id == R.id.nav_home) {
            loadBusRoutes(); // 重載所有路線
            if (bottomNavigationView != null) {
                bottomNavigationView.setSelectedItemId(R.id.nav_home);
            }
        } else if (id == R.id.nav_favorite) {
            Intent intent = new Intent(this, FavoritesActivity.class);
            startActivity(intent);
            if (bottomNavigationView != null) {
                bottomNavigationView.setSelectedItemId(R.id.nav_favorite);
            }
        } else if (id == R.id.nav_search) {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
            if (bottomNavigationView != null) {
                bottomNavigationView.setSelectedItemId(R.id.nav_search);
            }
        }
        
        // 關閉抽屜式導航
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
    
    /**
     * 切換語言（中英文）
     */
    private void switchLanguage() {
        isEnglish = !isEnglish;
        
        // 設置語言環境
        Locale locale = isEnglish ? Locale.ENGLISH : Locale.TRADITIONAL_CHINESE;
        Resources resources = getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, dm);
        
        // 更新路線適配器的語言設置
        updateAdapterLanguage();
        
        // 重新創建活動以應用新的語言設置
        recreate();
    }
    
    /**
     * 更新路線適配器的語言設置
     */
    private void updateAdapterLanguage() {
        if (routeAdapter != null) {
            // 檢查當前系統語言設置
            Locale currentLocale = getResources().getConfiguration().getLocales().get(0);
            isEnglish = !currentLocale.getLanguage().equals(Locale.CHINESE.getLanguage());
            
            // 通知適配器更新語言設置
            routeAdapter.updateLanguageSetting(isEnglish);
        }
    }

    @Override
    public void onBackPressed() {
        // 如果抽屜菜單開啟，按返回鍵關閉它
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 記錄當前是否在顯示收藏列表模式
        boolean isShowingFavorites = routeAdapter != null && routeAdapter.isShowingFavoritesOnly();
        
        // 刷新收藏管理器數據
        favoriteManager.refresh();
        
        // 完全刷新路線狀態
        if (routeAdapter != null) {
            routeAdapter.reloadData();
            
            // 如果之前是在顯示收藏模式，確保繼續顯示收藏
            if (isShowingFavorites) {
                routeAdapter.showFavoritesOnly();
            }
        }
        
        // 更新語言設置
        updateAdapterLanguage();
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