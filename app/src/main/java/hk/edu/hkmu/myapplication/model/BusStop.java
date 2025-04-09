package hk.edu.hkmu.myapplication.model;

/**
 * 巴士站模型類
 */
public class BusStop {
    private String stopId;      // 站點編號
    private String routeId;     // 路線編號
    private String direction;   // 方向
    private String serviceType; // 服務類型
    private String sequence;    // 站點順序

    private String nameTC;      // 站點名稱(中文)
    private String nameEN;      // 站點名稱(英文)
    private String location;    // 位置

    public BusStop(String stopId, String routeId, String direction, String serviceType, String sequence) {
        this.stopId = stopId;
        this.routeId = routeId;
        this.direction = direction;
        this.serviceType = serviceType;
        this.sequence = sequence;
    }

    public String getStopId() {
        return stopId;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getDirection() {
        return direction;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getSequence() {
        return sequence;
    }

    public String getNameTC() {
        return nameTC;
    }

    public void setNameTC(String nameTC) {
        this.nameTC = nameTC;
    }

    public String getNameEN() {
        return nameEN;
    }

    public void setNameEN(String nameEN) {
        this.nameEN = nameEN;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
    
    // 根據語言獲取站點名稱
    public String getName(boolean isEnglish) {
        return isEnglish ? nameEN : nameTC;
    }
} 