package hk.edu.hkmu.myapplication.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hk.edu.hkmu.myapplication.R;
import hk.edu.hkmu.myapplication.model.BusStop;
import hk.edu.hkmu.myapplication.model.RouteEta;

/**
 * 巴士站點列表適配器
 */
public class BusStopAdapter extends RecyclerView.Adapter<BusStopAdapter.ViewHolder> {
    private static final String TAG = "BusStopAdapter";
    private List<BusStop> stopList = new ArrayList<>();
    private Map<String, List<RouteEta>> etaMap = new HashMap<>();
    private boolean isEnglish = false;
    
    public BusStopAdapter() {
        // check language
        Locale currentLocale = Locale.getDefault();
        isEnglish = !currentLocale.getLanguage().equals(Locale.CHINESE.getLanguage());
    }

    public void updateData(List<BusStop> newStops, List<RouteEta> etaList) {
        this.stopList = newStops;
        populateEtaMap(etaList);  // Populate the ETA map with the incoming data
        Log.d(TAG, "Updated adapter with " + newStops.size() + " stops and " + etaList.size() + " ETAs.");
        notifyDataSetChanged();
    }

    private void populateEtaMap(List<RouteEta> etaList) {
        etaMap.clear();
        for (RouteEta eta : etaList) {
            String stopId = eta.getStopId();
            if (!etaMap.containsKey(stopId)) {
                etaMap.put(stopId, new ArrayList<>());
            }
            etaMap.get(stopId).add(eta);
        }
        Log.d(TAG, "Populated etaMap with " + etaMap.size() + " entries.");
    }


    public void updateLanguageSetting(boolean isEnglish) {
        this.isEnglish = isEnglish;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bus_stop, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BusStop stop = stopList.get(position);
        
        // 设置站点名称（包括序号）
        String stopName = (position + 1) + ". " + (isEnglish ? stop.getNameEN() : stop.getNameTC());
        holder.stopName.setText(stopName);
        
        // 设置顶部和底部连接线的可见性
        holder.stopLineTop.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        holder.stopLineBottom.setVisibility(position == getItemCount() - 1 ? View.INVISIBLE : View.VISIBLE);
        
        // 设置站点图标颜色
        holder.stopIcon.setBackgroundResource(R.drawable.circle_background_red);
        
        // 设置每个位置的唯一ID，以便进行动画处理
        holder.itemView.setId(position);
        
        // 设置点击监听器（可选）
        holder.itemView.setOnClickListener(v -> {
            boolean shouldShow = holder.eta.getVisibility() != View.VISIBLE;
            holder.eta.setVisibility(shouldShow ? View.VISIBLE : View.GONE);

            if (shouldShow) {
                // loadEtaData(holder, stop.getStopId(), context, stop.getServiceType());
                displayEta(holder, stop.getStopId());
            }
        });
    }

    private void displayEta(ViewHolder holder, String stopId) {
        List<RouteEta> etaList = etaMap.get(stopId);
        if (etaList != null && !etaList.isEmpty()) {
            Log.d(TAG, "Displaying ETA for stopId: " + stopId + ", ETAs found: " + etaList.size());
            RouteEta earliestEta = getEarliestEta(etaList);
            if (earliestEta != null) {
                if (earliestEta.getMinutesRemaining() <= 0) {
                    holder.eta.setText(isEnglish ? "Arriving" : "即將到站");
                } else {
                    String text = isEnglish ? earliestEta.getMinutesRemaining() + " min" : earliestEta.getMinutesRemaining() + " 分鐘";
                    holder.eta.setText(text);
                }
            } else {
                holder.eta.setText(isEnglish ? "No buses" : "無班次");
            }
        } else {
            Log.d(TAG, "No ETA data found for stopId: " + stopId);
            holder.eta.setText(isEnglish ? "No ETA data" : "無到站資料");
        }
    }


    private RouteEta getEarliestEta(List<RouteEta> etaList) {
        RouteEta earliestEta = null;
        for (RouteEta eta : etaList) {
            if (eta.getMinutesRemaining() >= 0) {
                if (earliestEta == null || eta.getMinutesRemaining() < earliestEta.getMinutesRemaining()) {
                    earliestEta = eta;
                }
            }
        }
        return earliestEta;
    }


    @Override
    public int getItemCount() {
        return stopList.size();
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        View stopLineTop;
        View stopLineBottom;
        ImageView stopIcon;
        TextView stopName;
        TextView eta;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            stopLineTop = itemView.findViewById(R.id.stop_line_top);
            stopLineBottom = itemView.findViewById(R.id.stop_line_bottom);
            stopIcon = itemView.findViewById(R.id.stop_icon);
            stopName = itemView.findViewById(R.id.tv_stop_name);
            eta = itemView.findViewById(R.id.tv_eta);
        }
    }
} 