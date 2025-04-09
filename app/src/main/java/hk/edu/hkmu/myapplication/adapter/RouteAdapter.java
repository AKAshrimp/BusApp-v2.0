package hk.edu.hkmu.myapplication.adapter;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import hk.edu.hkmu.myapplication.R;
import hk.edu.hkmu.myapplication.RouteDetailActivity;
import hk.edu.hkmu.myapplication.RouteStopsActivity;
import hk.edu.hkmu.myapplication.model.BusRoute;
import hk.edu.hkmu.myapplication.utils.FavoriteManager;
import hk.edu.hkmu.myapplication.utils.FavoriteUtil;

public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.ViewHolder> 
        implements FavoriteManager.FavoriteChangeListener {
    private List<BusRoute> routeList = new ArrayList<>();
    private List<BusRoute> originalRouteList = new ArrayList<>(); // 保存原始数据
    private boolean isEnglish = false;
    private Context context;
    private FavoriteManager favoriteManager;
    private boolean showingFavoritesOnly = false;
    private ItemClickListener itemClickListener;
    
    /**
     * 定義點擊事件監聽器介面
     */
    public interface ItemClickListener {
        void onItemClick(BusRoute route);
    }
    
    /**
     * 設置點擊事件監聽器
     */
    public void setOnItemClickListener(ItemClickListener listener) {
        this.itemClickListener = listener;
    }
    
    public RouteAdapter(Context context) {
        this.context = context;
        this.favoriteManager = FavoriteManager.getInstance(context);
        
        // 註冊為收藏變更的監聽器
        favoriteManager.addListener(this);
        
        // 檢查當前語言設置
        Locale currentLocale = Locale.getDefault();
        isEnglish = !currentLocale.getLanguage().equals(Locale.CHINESE.getLanguage());
    }
    
    /**
     * 使用DiffUtil提高更新效率的更新数据方法
     */
    public void updateData(List<BusRoute> newRoutes) {
        // 移除重複路線
        Set<String> routeIds = new HashSet<>();
        List<BusRoute> uniqueRoutes = new ArrayList<>();
        
        // 先刷新FavoriteManager的數據
        favoriteManager.refresh();
        
        for (BusRoute route : newRoutes) {
            String key = route.getRouteId() + "_" + route.getDirection();
            if (!routeIds.contains(key)) {
                routeIds.add(key);
                
                // 檢查並設置收藏狀態
                String uniqueId = route.getUniqueId();
                boolean isFavorite = favoriteManager.isFavorite(uniqueId);
                route.setFavorite(isFavorite);
                
                uniqueRoutes.add(route);
            }
        }
        
        // 保存原始完整的路線列表
        this.originalRouteList = new ArrayList<>(uniqueRoutes);
        
        final List<BusRoute> newList;
        // 如果當前顯示的是收藏列表，則只顯示收藏的路線
        if (showingFavoritesOnly) {
            newList = filterFavorites(uniqueRoutes);
        } else {
            newList = uniqueRoutes;
        }
        
        // 使用DiffUtil计算差异并更新
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new BusRouteDiffCallback(routeList, newList));
        routeList = new ArrayList<>(newList);
        diffResult.dispatchUpdatesTo(this);
    }
    
    /**
     * 过滤收藏路线的DiffUtil回调类
     */
    private static class BusRouteDiffCallback extends DiffUtil.Callback {
        private final List<BusRoute> oldList;
        private final List<BusRoute> newList;
        
        public BusRouteDiffCallback(List<BusRoute> oldList, List<BusRoute> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }
        
        @Override
        public int getOldListSize() {
            return oldList.size();
        }
        
        @Override
        public int getNewListSize() {
            return newList.size();
        }
        
        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getUniqueId().equals(
                    newList.get(newItemPosition).getUniqueId());
        }
        
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            BusRoute oldRoute = oldList.get(oldItemPosition);
            BusRoute newRoute = newList.get(newItemPosition);
            return oldRoute.isFavorite() == newRoute.isFavorite() &&
                   oldRoute.getRouteId().equals(newRoute.getRouteId()) &&
                   oldRoute.getDirection().equals(newRoute.getDirection()) &&
                   oldRoute.getOriginTC().equals(newRoute.getOriginTC()) &&
                   oldRoute.getDestinationTC().equals(newRoute.getDestinationTC());
        }
    }
    
    /**
     * 過濾只顯示收藏的路線
     */
    private List<BusRoute> filterFavorites(List<BusRoute> routes) {
        List<BusRoute> favorites = new ArrayList<>();
        for (BusRoute route : routes) {
            if (route.isFavorite()) {
                favorites.add(route);
            }
        }
        return favorites;
    }
    
    /**
     * 更新语言设置
     */
    public void updateLanguageSetting(boolean isEnglish) {
        if (this.isEnglish == isEnglish) return; // 避免不必要的刷新
        
        this.isEnglish = isEnglish;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bus_route, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BusRoute route = routeList.get(position);
        String routeNumber = route.getRouteId();
        holder.routeNumber.setText(routeNumber);

        // 根據語言設置顯示起終點中文或英文名稱
        if (isEnglish) {
            // 英文顯示
            String originEN = route.getOriginEN();
            String destinationEN = route.getDestinationEN();
            holder.routeDirection.setText(originEN + " → " + destinationEN);
        } else {
            // 中文顯示
            String originTC = route.getOriginTC();
            String destinationTC = route.getDestinationTC();
            holder.routeDirection.setText(originTC + " → " + destinationTC);
        }
        
        // 設置收藏按鈕
        setupFavoriteButton(holder, route);

        // 直接點擊路線項時跳轉到站點列表
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(route);
            } else {
                String normalizedDirection = normalizeRouteDirection(route.getDirection());
                try {
                    Intent intent = new Intent(v.getContext(), RouteStopsActivity.class);
                    intent.putExtra("routeId", route.getRouteId());
                    intent.putExtra("direction", normalizedDirection);
                    intent.putExtra("serviceType", route.getServiceType());
                    intent.putExtra("originTC", route.getOriginTC());
                    intent.putExtra("destinationTC", route.getDestinationTC());
                    intent.putExtra("originEN", route.getOriginEN());
                    intent.putExtra("destEN", route.getDestinationEN());
                    v.getContext().startActivity(intent);
                } catch (Exception e) {
                    Log.e("RouteAdapter", "啟動站點列表活動時出錯", e);
                }
            }
        });

        // 長按顯示更多路線信息
        holder.itemView.setOnLongClickListener(v -> {
            showRouteDetails(route);
            return true;
        });
    }
    
    /**
     * 收藏按鈕點擊事件處理
     */
    private void setupFavoriteButton(ViewHolder holder, BusRoute route) {
        // 使用FavoriteUtil統一處理收藏功能
        FavoriteUtil.setupFavoriteButton(
            context, 
            holder.btnFavorite, 
            route,
            (updatedRoute, isFavorite) -> {
                // 如果顯示的是收藏列表，且取消了收藏，則從列表中移除
                if (showingFavoritesOnly && !isFavorite) {
                    int position = routeList.indexOf(updatedRoute);
                    if (position != -1) {
                        routeList.remove(position);
                        notifyItemRemoved(position);
                    }
                }
            }
        );
    }
    
    /**
     * 格式化路線方向，確保API能正確處理
     * 統一方向格式，將i/o縮寫轉換為完整的inbound/outbound
     */
    private String normalizeRouteDirection(String direction) {
        if (direction == null) {
            return "outbound"; // 預設方向
        }
        
        if ("i".equalsIgnoreCase(direction) || "I".equals(direction)) {
            return "inbound";
        } else if ("o".equalsIgnoreCase(direction) || "O".equals(direction)) {
            return "outbound";
        }
        return direction; // 如果已經是完整格式則保持不變
    }
    
    /**
     * 生成路線唯一標識，用於收藏功能
     * 此方法應該與BusRoute.getUniqueId()邏輯保持一致
     */
    private String generateUniqueId(String routeId, String direction, String serviceType) {
        // 如果serviceType為空，預設使用"1"
        String safeServiceType = (serviceType == null || serviceType.isEmpty()) ? "1" : serviceType;
        
        // 確保方向使用標準格式
        String safeDirection = normalizeRouteDirection(direction);
        
        return routeId + "_" + safeDirection + "_" + safeServiceType;
    }
    
    /**
     * 顯示路線詳情的對話框
     */
    private void showRouteDetails(BusRoute route) {
        try {
            // 創建一個對話框來顯示路線詳情
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(route.getRouteId() + " 路線詳情");
            
            // 構建詳情內容
            StringBuilder details = new StringBuilder();
            if (isEnglish) {
                details.append("Origin: ").append(route.getOriginEN())
                       .append("\nDestination: ").append(route.getDestinationEN())
                       .append("\nDirection: ").append("inbound".equals(route.getDirection()) ? "Inbound" : "Outbound")
                       .append("\nService Type: ").append(route.getServiceType());
            } else {
                details.append("起點：").append(route.getOriginTC())
                       .append("\n終點：").append(route.getDestinationTC())
                       .append("\n方向：").append("inbound".equals(route.getDirection()) ? "入站" : "出站")
                       .append("\n服務類型：").append(route.getServiceType());
            }
            
            builder.setMessage(details.toString());
            builder.setPositiveButton("查看站點", (dialog, which) -> {
                // 格式化方向參數
                String normalizedDirection = normalizeRouteDirection(route.getDirection());
                
                // 啟動站點列表活動
                Intent intent = new Intent(context, RouteStopsActivity.class);
                intent.putExtra("routeId", route.getRouteId());
                intent.putExtra("direction", normalizedDirection);
                intent.putExtra("serviceType", route.getServiceType());
                intent.putExtra("originTC", route.getOriginTC());
                intent.putExtra("destTC", route.getDestinationTC());
                intent.putExtra("originEN", route.getOriginEN());
                intent.putExtra("destEN", route.getDestinationEN());
                context.startActivity(intent);
            });
            builder.setNegativeButton("關閉", null);
            builder.show();
        } catch (Exception e) {
            Log.e("RouteAdapter", "顯示路線詳情對話框時出錯", e);
        }
    }
    
    /**
     * 更新收藏狀態
     */
    public void refreshFavoriteStatus() {
        // 強制刷新FavoriteManager的數據
        favoriteManager.refresh();
        
        // 更新列表中每條路線的收藏狀態
        for (BusRoute route : originalRouteList) {
            String uniqueId = route.getUniqueId();
            boolean isFavorite = favoriteManager.isFavorite(uniqueId);
            route.setFavorite(isFavorite);
            Log.d("RouteAdapter", "Refresh favorite status: " + uniqueId + " is " + isFavorite);
        }
        
        // 如果當前顯示的是收藏列表，重新過濾
        if (showingFavoritesOnly) {
            routeList = filterFavorites(originalRouteList);
        }
        
        // 刷新顯示
        notifyDataSetChanged();
    }
    
    /**
     * 檢查是否正在顯示收藏列表
     * @return 是否只顯示收藏的路線
     */
    public boolean isShowingFavoritesOnly() {
        return showingFavoritesOnly;
    }
    
    /**
     * 切換為只顯示收藏的路線
     */
    public void showFavoritesOnly() {
        if (showingFavoritesOnly) {
            Log.d("RouteAdapter", "已經在顯示收藏列表，無需切換");
            return;
        }
        
        showingFavoritesOnly = true;
        
        // 強制刷新收藏管理器數據
        favoriteManager.refresh();
        
        // 過濾出收藏的路線
        List<BusRoute> favorites = new ArrayList<>();
        for (BusRoute route : originalRouteList) {
            // 檢查收藏狀態
            String uniqueId = route.getUniqueId();
            boolean isFavorite = favoriteManager.isFavorite(uniqueId);
            route.setFavorite(isFavorite);
            
            if (isFavorite) {
                favorites.add(route);
            }
        }
        
        // 更新顯示列表
        routeList = favorites;
        notifyDataSetChanged();
        
        Log.d("RouteAdapter", "切換為收藏列表模式，顯示 " + routeList.size() + " 條路線");
    }
    
    /**
     * 切換為顯示所有路線
     */
    public void showAllRoutes() {
        if (!showingFavoritesOnly) {
            Log.d("RouteAdapter", "已經在顯示全部路線，無需切換");
            return;
        }
        
        showingFavoritesOnly = false;
        
        // 更新所有路線的收藏狀態
        for (BusRoute route : originalRouteList) {
            String uniqueId = route.getUniqueId();
            boolean isFavorite = favoriteManager.isFavorite(uniqueId);
            route.setFavorite(isFavorite);
        }
        
        // 恢復顯示全部路線
        routeList = new ArrayList<>(originalRouteList);
        notifyDataSetChanged();
        
        Log.d("RouteAdapter", "切換為全部路線模式，顯示 " + routeList.size() + " 條路線");
    }
    
    /**
     * 實現FavoriteChangeListener接口的方法
     * 當收藏狀態改變時被調用
     */
    @Override
    public void onFavoriteChanged(String routeUniqueId, boolean isFavorite) {
        Log.d("RouteAdapter", "收藏狀態變更: " + routeUniqueId + " -> " + (isFavorite ? "已收藏" : "未收藏"));
        
        // 強制刷新收藏管理器中的數據
        favoriteManager.refresh();
        
        // 判斷路線是否在當前顯示的列表中
        boolean foundInCurrent = false;
        int positionInCurrent = -1;
        
        for (int i = 0; i < routeList.size(); i++) {
            BusRoute route = routeList.get(i);
            if (route.getUniqueId().equals(routeUniqueId)) {
                route.setFavorite(isFavorite);
                foundInCurrent = true;
                positionInCurrent = i;
                Log.d("RouteAdapter", "更新當前列表中的收藏狀態 - 路線: " + route.getRouteId() + 
                      ", 位置: " + i + ", 新狀態: " + (isFavorite ? "已收藏" : "未收藏"));
                break;
            }
        }
        
        // 更新原始列表中的收藏狀態
        boolean foundInOriginal = false;
        for (BusRoute route : originalRouteList) {
            if (route.getUniqueId().equals(routeUniqueId)) {
                route.setFavorite(isFavorite);
                foundInOriginal = true;
                Log.d("RouteAdapter", "更新原始列表中的收藏狀態 - 路線: " + route.getRouteId());
                break;
            }
        }
        
        // 如果當前顯示的是收藏路線，需要特殊處理
        if (showingFavoritesOnly) {
            if (foundInCurrent && !isFavorite) {
                // 如果路線在當前列表中且取消了收藏，則從列表中移除
                routeList.remove(positionInCurrent);
                notifyItemRemoved(positionInCurrent);
                Log.d("RouteAdapter", "從收藏列表中移除已取消收藏的路線，位置: " + positionInCurrent);
            } else if (!foundInCurrent && isFavorite && foundInOriginal) {
                // 如果路線不在當前列表中但已收藏且在原始列表中，則添加到當前列表
                for (BusRoute route : originalRouteList) {
                    if (route.getUniqueId().equals(routeUniqueId)) {
                        routeList.add(route);
                        notifyItemInserted(routeList.size() - 1);
                        Log.d("RouteAdapter", "向收藏列表添加新收藏的路線: " + route.getRouteId());
                        break;
                    }
                }
            }
        } else if (foundInCurrent) {
            // 如果顯示的是全部路線且路線在當前列表中，更新該項目
            notifyItemChanged(positionInCurrent);
            Log.d("RouteAdapter", "更新路線項目, 位置: " + positionInCurrent);
        }
    }
    
    /**
     * 完全重新加载数据，包括收藏状态
     * 这个方法用于确保收藏变化同步到所有页面
     */
    public void reloadData() {
        // 刷新收藏管理器数据
        favoriteManager.refresh();
        
        Log.d("RouteAdapter", "重新加载数据，原始列表大小：" + originalRouteList.size());
        
        // 为所有路线更新收藏状态
        for (BusRoute route : originalRouteList) {
            String uniqueId = route.getUniqueId();
            boolean isFavorite = favoriteManager.isFavorite(uniqueId);
            route.setFavorite(isFavorite);
            Log.d("RouteAdapter", "更新路线 " + route.getRouteId() + " 收藏状态: " + isFavorite);
        }
        
        // 根据当前模式过滤路线
        if (showingFavoritesOnly) {
            routeList = filterFavorites(originalRouteList);
            Log.d("RouteAdapter", "收藏模式，筛选后大小：" + routeList.size());
        } else {
            routeList = new ArrayList<>(originalRouteList);
            Log.d("RouteAdapter", "全部模式，列表大小：" + routeList.size());
        }
        
        // 通知视图刷新
        notifyDataSetChanged();
    }
    
    @Override
    public int getItemCount() {
        return routeList.size();
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView routeNumber;
        TextView routeDirection;
        ImageView btnFavorite;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            routeNumber = itemView.findViewById(R.id.route_number);
            routeDirection = itemView.findViewById(R.id.route_direction);
            btnFavorite = itemView.findViewById(R.id.btn_favorite);
        }
    }
} 