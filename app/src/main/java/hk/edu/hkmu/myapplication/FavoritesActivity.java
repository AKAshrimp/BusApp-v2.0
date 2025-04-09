package hk.edu.hkmu.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import hk.edu.hkmu.myapplication.adapter.RouteAdapter;
import hk.edu.hkmu.myapplication.api.BusApiClient;
import hk.edu.hkmu.myapplication.model.BusRoute;
import hk.edu.hkmu.myapplication.model.RouteEta;
import hk.edu.hkmu.myapplication.utils.FavoriteManager;
import hk.edu.hkmu.myapplication.utils.FavoriteUtil;

/**
 * 收藏路線頁面，專門用於顯示用戶收藏的路線
 */
public class FavoritesActivity extends AppCompatActivity implements FavoriteManager.FavoriteChangeListener {

    private static final String TAG = "FavoritesActivity";
    private RecyclerView recyclerView;
    private RouteAdapter routeAdapter;
    private TextView noFavoritesText;
    private List<BusRoute> allRoutes = new ArrayList<>();
    private BusApiClient busApiClient;
    private boolean isEnglish = false;
    private FavoriteManager favoriteManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        // 設置工具欄
        Toolbar toolbar = findViewById(R.id.toolbar_favorites);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.favorites);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 初始化收藏管理器
        favoriteManager = FavoriteManager.getInstance(this);
        favoriteManager.addListener(this);

        // 檢查當前語言設置
        checkCurrentLanguage();

        // 初始化RecyclerView
        recyclerView = findViewById(R.id.rv_favorites);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 設置空視圖
        noFavoritesText = findViewById(R.id.tv_no_favorites);
        
        // 創建適配器
        routeAdapter = new RouteAdapter(this);
        recyclerView.setAdapter(routeAdapter);
        
        // 初始化API客戶端並加載所有路線
        busApiClient = new BusApiClient();
        loadAllRoutes();
        
        // 設置底部導航欄
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_favorite);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                
                if (id == R.id.nav_home) {
                    // 返回主頁
                    Intent intent = new Intent(FavoritesActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                    return true;
                } else if (id == R.id.nav_search) {
                    // 前往搜索頁面
                    Intent intent = new Intent(FavoritesActivity.this, SearchActivity.class);
                    startActivity(intent);
                    finish();
                    return true;
                } else if (id == R.id.nav_favorite) {
                    // 已經在收藏頁面，不需要操作
                    return true;
                }
                
                return false;
            }
        });
    }
    
    /**
     * 檢查當前系統語言設置
     */
    private void checkCurrentLanguage() {
        Locale currentLocale = getResources().getConfiguration().getLocales().get(0);
        isEnglish = !currentLocale.getLanguage().equals(Locale.CHINESE.getLanguage());
        
        // 更新RouteAdapter的語言設置
        if (routeAdapter != null) {
            routeAdapter.updateLanguageSetting(isEnglish);
        }
        
        // 更新標題和提示文字
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.favorites);
        }
    }
    
    /**
     * 加載所有路線數據
     */
    private void loadAllRoutes() {
        busApiClient.getAllRoutes(new BusApiClient.ApiCallback<List<BusRoute>>() {
            @Override
            public List<RouteEta> onSuccess(List<BusRoute> result) {
                allRoutes = result;
                loadFavoriteRoutes();
                return null;
            }

            @Override
            public void onError(String errorMessage) {
                // 如果加載失敗，使用模擬數據
                allRoutes = createMockBusRoutes();
                loadFavoriteRoutes();
            }
        });
    }
    
    /**
     * 加載收藏的路線
     */
    private void loadFavoriteRoutes() {
        // 確保收藏管理器數據是最新的
        favoriteManager.refresh();
        
        // 從所有路線中篩選出收藏的路線
        List<BusRoute> favoriteRoutes = new ArrayList<>();
        Set<String> favoriteIds = favoriteManager.getAllFavorites();
        
        // 如果還沒有加載所有路線，則直接返回空列表
        if (allRoutes.isEmpty()) {
            updateUI(favoriteRoutes);
            return;
        }
        
        for (BusRoute route : allRoutes) {
            String uniqueId = FavoriteUtil.standardizeRouteId(
                    route.getRouteId(), 
                    route.getDirection(), 
                    route.getServiceType());
            
            if (favoriteIds.contains(uniqueId)) {
                route.setFavorite(true);
                favoriteRoutes.add(route);
            }
        }
        
        // 更新UI
        updateUI(favoriteRoutes);
    }
    
    /**
     * 更新UI顯示
     */
    private void updateUI(List<BusRoute> favoriteRoutes) {
        if (favoriteRoutes.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            noFavoritesText.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            noFavoritesText.setVisibility(View.GONE);
            routeAdapter.updateData(favoriteRoutes);
        }
    }

    /**
     * 創建模擬路線數據，當API調用失敗時使用
     */
    private List<BusRoute> createMockBusRoutes() {
        List<BusRoute> mockRoutes = new ArrayList<>();
        mockRoutes.add(new BusRoute("1", "中環", "Central", "石塘咀", "Shek Tong Tsui", "outbound", "1"));
        mockRoutes.add(new BusRoute("2", "中環", "Central", "筲箕灣", "Shau Kei Wan", "outbound", "1"));
        mockRoutes.add(new BusRoute("6", "中環", "Central", "赤柱", "Stanley", "outbound", "1"));
        // 添加更多路線...
        return mockRoutes;
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 更新語言設置
        checkCurrentLanguage();
        
        // 刷新收藏數據
        loadFavoriteRoutes();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 移除監聽器
        favoriteManager.removeListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onFavoriteChanged(String routeUniqueId, boolean isFavorite) {
        // 當收藏狀態變更時，重新加載收藏列表
        loadFavoriteRoutes();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 不在收藏頁面顯示選項菜單
        return true;
    }
} 