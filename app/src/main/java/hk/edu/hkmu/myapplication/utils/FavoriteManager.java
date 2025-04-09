package hk.edu.hkmu.myapplication.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * 收藏管理工具類
 * 負責處理路線收藏的保存和讀取
 * 使用JSON格式存儲數據以提高靈活性和可靠性
 */
public class FavoriteManager {
    private static final String TAG = "FavoriteManager";
    private static final String PREFS_NAME = "bus_favorites";
    private static final String KEY_FAVORITES = "favorite_routes_json";
    private static final String KEY_LEGACY_FAVORITES = "favorite_routes"; // 舊版的key，用於遷移數據

    private static volatile FavoriteManager instance;
    private final SharedPreferences sharedPreferences;
    private JSONObject favoriteRoutes;
    private Set<FavoriteChangeListener> listeners = new HashSet<>();

    private FavoriteManager(Context context) {
        // 使用應用上下文以避免內存洩漏
        Context appContext = context.getApplicationContext();
        sharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFavorites();
    }

    /**
     * 獲取FavoriteManager的單例實例
     * 使用雙重檢查鎖定模式以確保線程安全
     */
    public static FavoriteManager getInstance(Context context) {
        if (instance == null) {
            synchronized (FavoriteManager.class) {
                if (instance == null) {
                    instance = new FavoriteManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 從SharedPreferences加載收藏數據
     */
    private void loadFavorites() {
        try {
            // 嘗試讀取JSON格式的收藏數據
            String jsonStr = sharedPreferences.getString(KEY_FAVORITES, null);
            
            // 如果沒有JSON數據，檢查是否有舊版的收藏數據需要遷移
            if (jsonStr == null) {
                Set<String> legacyFavorites = sharedPreferences.getStringSet(KEY_LEGACY_FAVORITES, new HashSet<>());
                // 如果有舊數據，將其遷移到JSON格式
                if (!legacyFavorites.isEmpty()) {
                    Log.d(TAG, "發現舊版收藏數據，正在遷移到JSON格式...");
                    favoriteRoutes = new JSONObject();
                    for (String id : legacyFavorites) {
                        favoriteRoutes.put(id, true);
                    }
                    // 保存遷移後的數據
                    saveFavorites();
                    // 刪除舊數據
                    sharedPreferences.edit().remove(KEY_LEGACY_FAVORITES).apply();
                    Log.d(TAG, "舊版收藏數據遷移完成，共" + legacyFavorites.size() + "項");
                    return;
                } else {
                    // 如果沒有任何數據，創建一個空的JSON對象
                    favoriteRoutes = new JSONObject();
                }
            } else {
                // 解析JSON字符串
                favoriteRoutes = new JSONObject(jsonStr);
            }
            
            // 打印日誌
            Log.d(TAG, "已加載收藏數據: " + favoriteRoutes.toString());
            Log.d(TAG, "收藏項目數量: " + countFavorites());
        } catch (Exception e) {
            Log.e(TAG, "解析JSON收藏數據失敗", e);
            // 如果解析失敗，創建一個新的空JSON對象
            favoriteRoutes = new JSONObject();
        }
    }
    
    /**
     * 計算當前收藏數量
     */
    private int countFavorites() {
        int count = 0;
        Iterator<String> keys = favoriteRoutes.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (favoriteRoutes.optBoolean(key, false)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 添加路線到收藏
     * @param routeUniqueId 路線唯一標識
     * @return 添加是否成功
     */
    public boolean addFavorite(String routeUniqueId) {
        // 先刷新確保使用最新數據
        loadFavorites();
        
        try {
            // 檢查是否已經收藏
            if (favoriteRoutes.optBoolean(routeUniqueId, false)) {
                Log.d(TAG, "路線已經在收藏列表中: " + routeUniqueId);
                return false;
            }
            
            // 添加到收藏
            favoriteRoutes.put(routeUniqueId, true);
            saveFavorites();
            notifyListeners(routeUniqueId, true);
            Log.d(TAG, "已添加收藏: " + routeUniqueId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "添加收藏失敗: " + routeUniqueId, e);
            return false;
        }
    }

    /**
     * 從收藏中移除路線
     * @param routeUniqueId 路線唯一標識
     * @return 移除是否成功
     */
    public boolean removeFavorite(String routeUniqueId) {
        // 先刷新確保使用最新數據
        loadFavorites();
        
        try {
            // 檢查是否已經收藏
            if (!favoriteRoutes.optBoolean(routeUniqueId, false)) {
                Log.d(TAG, "路線不在收藏列表中，無需移除: " + routeUniqueId);
                return false;
            }
            
            // 從收藏中移除
            favoriteRoutes.remove(routeUniqueId);
            saveFavorites();
            notifyListeners(routeUniqueId, false);
            Log.d(TAG, "已移除收藏: " + routeUniqueId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "移除收藏失敗: " + routeUniqueId, e);
            return false;
        }
    }

    /**
     * 檢查路線是否已收藏
     * @param routeUniqueId 路線唯一標識
     * @return 是否已收藏
     */
    public boolean isFavorite(String routeUniqueId) {
        // 確保使用最新數據
        loadFavorites();
        
        boolean result = favoriteRoutes.optBoolean(routeUniqueId, false);
        Log.d(TAG, "檢查收藏狀態 - 路線: " + routeUniqueId + ", 結果: " + (result ? "已收藏" : "未收藏"));
        return result;
    }

    /**
     * 切換路線的收藏狀態
     * @param routeUniqueId 路線唯一標識
     * @return 切換後的狀態，true表示已收藏，false表示未收藏
     */
    public boolean toggleFavorite(String routeUniqueId) {
        // 確保使用最新數據
        loadFavorites();
        
        try {
            // 獲取當前狀態並切換
            boolean currentState = favoriteRoutes.optBoolean(routeUniqueId, false);
            boolean newState = !currentState;
            
            Log.d(TAG, "切換收藏狀態 - 路線: " + routeUniqueId + 
                  ", 當前狀態: " + (currentState ? "已收藏" : "未收藏") + 
                  ", 新狀態: " + (newState ? "已收藏" : "未收藏"));
            
            if (newState) {
                favoriteRoutes.put(routeUniqueId, true);
            } else {
                favoriteRoutes.remove(routeUniqueId);
            }
            
            saveFavorites();
            notifyListeners(routeUniqueId, newState);
            return newState;
        } catch (Exception e) {
            Log.e(TAG, "切換收藏狀態失敗: " + routeUniqueId, e);
            return false;
        }
    }

    /**
     * 獲取所有收藏的路線ID
     * @return 收藏的路線ID集合
     */
    public Set<String> getAllFavorites() {
        // 確保使用最新數據
        loadFavorites();
        
        Set<String> result = new HashSet<>();
        Iterator<String> keys = favoriteRoutes.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (favoriteRoutes.optBoolean(key, false)) {
                result.add(key);
            }
        }
        
        Log.d(TAG, "獲取所有收藏，數量: " + result.size());
        return result;
    }

    /**
     * 強制刷新收藏列表
     * 從SharedPreferences重新加載數據
     */
    public void refresh() {
        Log.d(TAG, "強制刷新收藏數據");
        loadFavorites();
    }

    /**
     * 保存收藏數據到SharedPreferences
     */
    private void saveFavorites() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_FAVORITES, favoriteRoutes.toString());
        editor.apply();
        Log.d(TAG, "已保存收藏數據: " + favoriteRoutes.toString());
        Log.d(TAG, "收藏項目數量: " + countFavorites());
    }
    
    /**
     * 添加收藏變更監聽器
     */
    public void addListener(FavoriteChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
            Log.d(TAG, "添加收藏變更監聽器: " + listener.getClass().getSimpleName());
        }
    }
    
    /**
     * 移除收藏變更監聽器
     */
    public void removeListener(FavoriteChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
            Log.d(TAG, "移除收藏變更監聽器: " + listener.getClass().getSimpleName());
        }
    }
    
    /**
     * 通知所有監聽器收藏狀態變更
     */
    private void notifyListeners(String routeUniqueId, boolean isFavorite) {
        Log.d(TAG, "通知所有監聽器收藏狀態變更 - 路線: " + routeUniqueId + 
              ", 狀態: " + (isFavorite ? "已收藏" : "未收藏") + 
              ", 監聽器數量: " + listeners.size());
              
        for (FavoriteChangeListener listener : listeners) {
            try {
                listener.onFavoriteChanged(routeUniqueId, isFavorite);
                Log.d(TAG, "已通知監聽器: " + listener.getClass().getSimpleName());
            } catch (Exception e) {
                Log.e(TAG, "通知監聽器時發生錯誤: " + listener.getClass().getSimpleName(), e);
            }
        }
    }
    
    /**
     * 收藏變更監聽器介面
     */
    public interface FavoriteChangeListener {
        void onFavoriteChanged(String routeUniqueId, boolean isFavorite);
    }
} 