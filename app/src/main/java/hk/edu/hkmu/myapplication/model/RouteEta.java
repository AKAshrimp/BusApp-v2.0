package hk.edu.hkmu.myapplication.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * 巴士預計到達時間模型類
 */
public class RouteEta {
    private String routeId;      // 路線編號
    private String stopId;       // 站點編號
    private String direction;    // 方向
    private String serviceType;  // 服務類型
    private String etaTime;      // 預計到達時間 (ISO8601 format)
    private String remarkTC;     // 備註(中文)
    private String remarkEN;     // 備註(英文)
    
    private long minutesRemaining = -1; // 剩餘分鐘數

    public RouteEta(String routeId, String stopId, String direction, String serviceType, 
                   String etaTime, String remarkTC, String remarkEN) {
        this.routeId = routeId;
        this.stopId = stopId;
        this.direction = direction;
        this.serviceType = serviceType;
        this.etaTime = etaTime;
        this.remarkTC = remarkTC;
        this.remarkEN = remarkEN;
        
        calculateMinutesRemaining();
    }

    public String getRouteId() {
        return routeId;
    }

    public String getStopId() {
        return stopId;
    }

    public String getDirection() {
        return direction;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getEtaTime() {
        return etaTime;
    }

    public String getRemarkTC() {
        return remarkTC;
    }

    public String getRemarkEN() {
        return remarkEN;
    }
    
    public long getMinutesRemaining() {
        return minutesRemaining;
    }
    
    // 根據語言獲取備註
    public String getRemark(boolean isEnglish) {
        return isEnglish ? remarkEN : remarkTC;
    }




    private void calculateMinutesRemaining() {
        if (etaTime == null || etaTime.isEmpty()) {
            minutesRemaining = -1;
            return;
        }

        try {
            minutesRemaining = calculateMinutes(etaTime, "yyyy-MM-dd'T'HH:mm:ssXXX");
        } catch (ParseException e) {
            // Attempt with a different format if the first fails
            try {
                minutesRemaining = calculateMinutes(etaTime, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            } catch (ParseException e2) {
                System.out.println("ETA時間格式無法解析: " + etaTime + ", 錯誤: " + e.getMessage());
                minutesRemaining = -1;
            }
        }
    }

    private long calculateMinutes(String etaTime, String format) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // Set time zone to UTC
        Date etaDate = sdf.parse(etaTime);
        Date now = new Date();

        // Debug logs
        System.out.println("ETA計算: 路線=" + routeId + ", 站點=" + stopId +
                ", 原始時間=" + etaTime +
                ", 解析時間=" + etaDate +
                ", 當前時間=" + now);

        long diffInMillis = etaDate.getTime() - now.getTime();
        long minutesRemaining = TimeUnit.MILLISECONDS.toMinutes(diffInMillis);

        // Debug log
        System.out.println("ETA計算結果: 毫秒差=" + diffInMillis + ", 分鐘差=" + minutesRemaining);

        // Adjust for display purposes
        if (minutesRemaining < -1) {
            return -1; // Already passed
        } else if (minutesRemaining < 0) {
            return 0; // Arriving soon
        }
        return minutesRemaining;
    }



} 