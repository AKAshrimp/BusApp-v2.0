package hk.edu.hkmu.myapplication.utils;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.util.List;

import hk.edu.hkmu.myapplication.model.BusRoute;

/**
 * 巴士路線收藏工具類
 * 提供統一的收藏按鈕處理邏輯，確保所有頁面收藏功能一致
 */
public class FavoriteUtil {
    
    private static final String TAG = "FavoriteUtil";
    
    /**
     * 設置收藏按鈕點擊事件，確保所有頁面行為一致
     * 
     * @param context 上下文
     * @param favoriteButton 收藏按鈕
     * @param route 巴士路線對象
     * @param callback 收藏狀態變更回調
     */
    public static void setupFavoriteButton(
            Context context, 
            ImageView favoriteButton, 
            BusRoute route,
            FavoriteCallback callback) {
        
        // 獲取FavoriteManager實例
        FavoriteManager favoriteManager = FavoriteManager.getInstance(context);
        
        // 檢查路線是否已收藏
        String uniqueId = route.getUniqueId();
        boolean isFavorite = favoriteManager.isFavorite(uniqueId);
        
        // 更新路線的收藏狀態
        route.setFavorite(isFavorite);
        
        // 更新按鈕的選中狀態
        favoriteButton.setSelected(isFavorite);
        
        // 設置按鈕點擊事件
        favoriteButton.setOnClickListener(v -> {
            // 切換收藏狀態
            boolean newState = favoriteManager.toggleFavorite(uniqueId);
            
            // 更新路線對象和按鈕顯示
            route.setFavorite(newState);
            favoriteButton.setSelected(newState);
            
            // 如果有回調，通知收藏狀態變更
            if (callback != null) {
                callback.onFavoriteChanged(route, newState);
            }
            
            Log.d(TAG, "路線 " + route.getRouteId() + " 收藏狀態變更為: " + (newState ? "已收藏" : "未收藏"));
        });
    }
    
    /**
     * 更新路線列表中所有路線的收藏狀態
     * 
     * @param context 上下文
     * @param routes 需要更新的路線列表
     */
    public static void updateRoutesFavoriteStatus(Context context, List<BusRoute> routes) {
        if (routes == null || routes.isEmpty()) {
            return;
        }
        
        FavoriteManager favoriteManager = FavoriteManager.getInstance(context);
        favoriteManager.refresh();
        
        for (BusRoute route : routes) {
            String uniqueId = route.getUniqueId();
            boolean isFavorite = favoriteManager.isFavorite(uniqueId);
            route.setFavorite(isFavorite);
        }
    }
    
    /**
     * 過濾列表僅保留收藏的路線
     * 
     * @param context 上下文
     * @param routes 原始路線列表
     * @return 只包含收藏路線的新列表
     */
    public static List<BusRoute> filterFavorites(Context context, List<BusRoute> routes) {
        // 先更新所有路線的收藏狀態
        updateRoutesFavoriteStatus(context, routes);
        
        // 創建新列表存放收藏的路線
        List<BusRoute> favorites = new java.util.ArrayList<>();
        
        // 篩選收藏的路線
        for (BusRoute route : routes) {
            if (route.isFavorite()) {
                favorites.add(route);
            }
        }
        
        return favorites;
    }
    
    /**
     * 收藏狀態變更回調接口
     */
    public interface FavoriteCallback {
        /**
         * 當收藏狀態變更時調用
         * 
         * @param route 巴士路線對象
         * @param isFavorite 新的收藏狀態
         */
        void onFavoriteChanged(BusRoute route, boolean isFavorite);
    }

    /**
     * 標準化路線ID，確保在不同頁面使用相同的ID格式
     * 
     * @param routeId 路線編號
     * @param direction 方向
     * @param serviceType 服務類型
     * @return 標準化的唯一ID
     */
    public static String standardizeRouteId(String routeId, String direction, String serviceType) {
        // 確保路線ID不為空
        if (routeId == null || routeId.isEmpty()) {
            Log.e(TAG, "路線ID為空，無法標準化路線ID");
            return "";
        }
        
        // 標準化路線ID（去除空格並轉為大寫）
        String normalizedRouteId = routeId.trim().toUpperCase();
        
        // 確保方向使用統一格式
        String normalizedDirection = normalizeDirection(direction);
        Log.d(TAG, "標準化方向: " + direction + " -> " + normalizedDirection);
        
        // 服務類型預設為"1"
        String normalizedServiceType = (serviceType == null || serviceType.isEmpty()) ? "1" : serviceType.trim();
        Log.d(TAG, "標準化服務類型: " + serviceType + " -> " + normalizedServiceType);
        
        // 返回統一格式的ID
        String uniqueId = normalizedRouteId + "_" + normalizedDirection + "_" + normalizedServiceType;
        Log.d(TAG, "生成唯一ID: " + uniqueId + " (路線ID=" + routeId + ", 方向=" + direction + ", 服務類型=" + serviceType + ")");
        return uniqueId;
    }

    /**
     * 標準化方向格式
     * 加強處理各種可能的格式
     * 
     * @param direction 方向
     * @return 標準化的方向
     */
    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isEmpty()) {
            Log.d(TAG, "方向為空，使用預設方向：outbound");
            return "outbound"; // 預設方向
        }
        
        String normalizedDirection = direction.trim().toLowerCase();
        
        if ("i".equals(normalizedDirection) || 
            "in".equals(normalizedDirection) || 
            "inbound".equals(normalizedDirection)) {
            return "inbound";
        } else if ("o".equals(normalizedDirection) || 
                  "out".equals(normalizedDirection) || 
                  "outbound".equals(normalizedDirection)) {
            return "outbound";
        }
        
        // 嘗試通過數字處理
        if ("1".equals(normalizedDirection)) {
            return "inbound";
        } else if ("2".equals(normalizedDirection)) {
            return "outbound";
        }
        
        // 如果不符合任何已知格式，記錄警告並使用原始值
        Log.w(TAG, "無法識別的方向格式: " + direction + "，使用原始值");
        return normalizedDirection;
    }
} 