package hk.edu.hkmu.myapplication.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 巴士路線模型類
 * 實現Parcelable接口以支持在Activity間傳遞
 */
public class BusRoute implements Parcelable {
    private String routeId;       // 路線編號
    private String originTC;      // 起點(中文)
    private String originEN;      // 起點(英文)
    private String destinationTC; // 終點(中文)
    private String destinationEN; // 終點(英文)
    private String direction;     // 方向 (inbound/outbound)
    private String serviceType;   // 服務類型
    private int eta;              // 預計到達時間(分鐘)
    private boolean isFavorite;   // 收藏狀態

    // 用於顯示模擬數據的簡化構造函數
    public BusRoute(String routeId, String origin, String destination, String direction, int eta) {
        this.routeId = routeId;
        this.originTC = origin;
        this.destinationTC = destination;
        this.direction = direction;
        this.eta = eta;
        this.serviceType = "1"; // 設置預設的服務類型為"1"
        this.isFavorite = false;
    }
    
    // 從API創建對象的完整構造函數
    public BusRoute(String routeId, String originTC, String originEN, String destinationTC, 
                   String destinationEN, String direction, String serviceType) {
        this.routeId = routeId;
        this.originTC = originTC;
        this.originEN = originEN;
        this.destinationTC = destinationTC;
        this.destinationEN = destinationEN;
        this.direction = direction;
        this.serviceType = serviceType;
        this.eta = -1; // 未知時間
        this.isFavorite = false;
    }

    protected BusRoute(Parcel in) {
        routeId = in.readString();
        originTC = in.readString();
        originEN = in.readString();
        destinationTC = in.readString();
        destinationEN = in.readString();
        direction = in.readString();
        serviceType = in.readString();
        eta = in.readInt();
        isFavorite = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(routeId);
        dest.writeString(originTC);
        dest.writeString(originEN);
        dest.writeString(destinationTC);
        dest.writeString(destinationEN);
        dest.writeString(direction);
        dest.writeString(serviceType);
        dest.writeInt(eta);
        dest.writeByte((byte) (isFavorite ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BusRoute> CREATOR = new Creator<BusRoute>() {
        @Override
        public BusRoute createFromParcel(Parcel in) {
            return new BusRoute(in);
        }

        @Override
        public BusRoute[] newArray(int size) {
            return new BusRoute[size];
        }
    };

    public String getRouteId() {
        return routeId;
    }

    public String getOriginTC() {
        return originTC;
    }

    public String getOriginEN() {
        return originEN;
    }

    public String getDestinationTC() {
        return destinationTC;
    }

    public String getDestinationEN() {
        return destinationEN;
    }

    public String getDirection() {
        return direction;
    }

    public String getServiceType() {
        return serviceType;
    }

    public int getEta() {
        return eta;
    }
    
    public void setEta(int eta) {
        this.eta = eta;
    }
    
    // 生成唯一標識，用於收藏功能
    public String getUniqueId() {
        return hk.edu.hkmu.myapplication.utils.FavoriteUtil.standardizeRouteId(routeId, direction, serviceType);
    }
    
    // 獲取收藏狀態
    public boolean isFavorite() {
        return isFavorite;
    }
    
    // 設置收藏狀態
    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }
    
    // 切換收藏狀態
    public void toggleFavorite() {
        isFavorite = !isFavorite;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        BusRoute busRoute = (BusRoute) o;
        
        // 比較唯一標識
        return getUniqueId().equals(busRoute.getUniqueId());
    }
    
    @Override
    public int hashCode() {
        return getUniqueId().hashCode();
    }
    
    // 獲取起點終點文本 (根據語言)
    public String getOriginDestText(boolean isEnglish) {
        if (isEnglish) {
            return originEN + " - " + destinationEN;
        } else {
            return originTC + " - " + destinationTC;
        }
    }
    
    // 向前兼容原有代碼
    public String getOrigin() {
        return originTC;
    }
    
    public String getDestination() {
        return destinationTC;
    }
    
    public String getOriginDestText() {
        return originTC + " - " + destinationTC;
    }
} 