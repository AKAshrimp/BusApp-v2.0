package hk.edu.hkmu.myapplication;

import android.app.Application;
import android.util.Log;

import hk.edu.hkmu.myapplication.api.BusApiClient;

/**
 * 應用程序類，用於管理全局資源和生命週期
 */
public class BusApplication extends Application {
    private static final String TAG = "BusApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "巴士應用程序已啟動");
        
        // 初始化全局資源
        initResources();
    }
    
    /**
     * 初始化全局資源
     */
    private void initResources() {
        // 預初始化API客戶端
        BusApiClient.getInstance();
    }
    
    @Override
    public void onTerminate() {
        // 清理所有資源
        cleanup();
        super.onTerminate();
    }
    
    /**
     * 清理全局資源
     */
    private void cleanup() {
        Log.d(TAG, "清理全局資源");
        
        // 關閉API客戶端的線程池
        BusApiClient.shutdownExecutor();
    }
} 