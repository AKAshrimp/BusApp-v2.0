package hk.edu.hkmu.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import hk.edu.hkmu.myapplication.adapter.RouteAdapter;
import hk.edu.hkmu.myapplication.api.BusApiClient;
import hk.edu.hkmu.myapplication.model.BusRoute;
import hk.edu.hkmu.myapplication.model.RouteEta;
import hk.edu.hkmu.myapplication.utils.FavoriteManager;

public class SearchActivity extends AppCompatActivity implements FavoriteManager.FavoriteChangeListener {

    private static final String TAG = "SearchActivity";
    private RecyclerView recyclerView;
    private EditText searchEditText;
    private RouteAdapter routeAdapter;
    private List<BusRoute> allRoutes = new ArrayList<>();
    private BusApiClient busApiClient;
    private boolean isEnglish = false;
    private FavoriteManager favoriteManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        // 初始化FavoriteManager
        favoriteManager = FavoriteManager.getInstance(this);
        favoriteManager.addListener(this);
        
        // 檢查當前語言設置
        checkCurrentLanguage();
        
        // 初始化工具欄
        Toolbar toolbar = findViewById(R.id.toolbar_search);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.search_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));

        // 初始化視圖
        recyclerView = findViewById(R.id.rv_search_results);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        searchEditText = findViewById(R.id.et_search);
        
        // 創建適配器
        routeAdapter = new hk.edu.hkmu.myapplication.adapter.RouteAdapter(this);
        recyclerView.setAdapter(routeAdapter);
        
        // 設置搜索監聽
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterRoutes(s.toString());
            }
        });
        
        // 初始化API客戶端並加載所有路線
        busApiClient = new BusApiClient();
        loadAllRoutes();
        
        // 設置底部導航欄
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_search);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                
                if (id == R.id.nav_home) {
                    // 返回主頁
                    Intent intent = new Intent(SearchActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                    return true;
                } else if (id == R.id.nav_favorite) {
                    // 前往收藏頁面
                    Intent intent = new Intent(SearchActivity.this, FavoritesActivity.class);
                    startActivity(intent);
                    finish();
                    return true;
                } else if (id == R.id.nav_search) {
                    // 已經在搜索頁面，不需要操作
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
        
        // 更新RouteAdapter語言設置
        if (routeAdapter != null) {
            routeAdapter.updateLanguageSetting(isEnglish);
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
                updateRouteFavoriteStatus();
                routeAdapter.updateData(allRoutes);
                return null;
            }

            @Override
            public void onError(String errorMessage) {
                // 如果加載失敗，使用模擬數據
                allRoutes = createMockBusRoutes();
                updateRouteFavoriteStatus();
                routeAdapter.updateData(allRoutes);
            }
        });
    }
    
    /**
     * 創建模擬數據，當API調用失敗時使用
     */
    private List<BusRoute> createMockBusRoutes() {
        List<BusRoute> mockRoutes = new ArrayList<>();
        mockRoutes.add(new BusRoute("1", "中環", "Central", "石塘咀", "Shek Tong Tsui", "outbound", "1"));
        mockRoutes.add(new BusRoute("2", "中環", "Central", "筲箕灣", "Shau Kei Wan", "outbound", "1"));
        // 添加更多模擬數據...
        return mockRoutes;
    }
    
    /**
     * 根據搜索文本過濾路線
     */
    private void filterRoutes(String query) {
        if (allRoutes == null || allRoutes.isEmpty()) {
            return;
        }
        
        query = query.trim().toLowerCase();
        
        if (query.isEmpty()) {
            routeAdapter.updateData(allRoutes);
            return;
        }
        
        List<BusRoute> filteredRoutes = new ArrayList<>();
        for (BusRoute route : allRoutes) {
            String routeId = route.getRouteId().toLowerCase();
            String originTC = route.getOriginTC().toLowerCase();
            String destTC = route.getDestinationTC().toLowerCase();
            String originEN = route.getOriginEN().toLowerCase();
            String destEN = route.getDestinationEN().toLowerCase();
            
            if (routeId.contains(query) || 
                originTC.contains(query) || destTC.contains(query) ||
                originEN.contains(query) || destEN.contains(query)) {
                filteredRoutes.add(route);
            }
        }
        
        routeAdapter.updateData(filteredRoutes);
    }
    
    /**
     * 更新所有路線的收藏狀態
     */
    private void updateRouteFavoriteStatus() {
        favoriteManager.refresh();
        
        for (BusRoute route : allRoutes) {
            String uniqueId = route.getUniqueId();
            boolean isFavorite = favoriteManager.isFavorite(uniqueId);
            route.setFavorite(isFavorite);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // 檢查並更新語言設置
        checkCurrentLanguage();
        
        // 重新應用搜索過濾器，以確保顯示最新數據
        filterRoutes(searchEditText.getText().toString());
        
        // 更新路線收藏狀態
        updateRouteFavoriteStatus();
        
        // 刷新列表顯示
        routeAdapter.notifyDataSetChanged();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 移除監聽器
        favoriteManager.removeListener(this);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onFavoriteChanged(String routeUniqueId, boolean isFavorite) {
        // 更新收藏狀態
        updateRouteFavoriteStatus();
        routeAdapter.notifyDataSetChanged();
    }
} 