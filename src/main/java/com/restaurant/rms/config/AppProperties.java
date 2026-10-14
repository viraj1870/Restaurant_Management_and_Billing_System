package com.restaurant.rms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Loyalty loyalty = new Loyalty();
    private final Notification notification = new Notification();
    private final Payment payment = new Payment();

    public Loyalty getLoyalty() { return loyalty; }
    public Notification getNotification() { return notification; }
    public Payment getPayment() { return payment; }

    public static class Loyalty {
        /** Customer earns 1 point for every this many rupees of the final bill. */
        private int earnPerAmount = 100;
        /** Rupee value of a single point when redeemed. */
        private int pointValue = 1;
        /** Max share of the (post-coupon) bill that can be paid with points. */
        private int maxRedeemPercent = 50;
        private int signupBonus = 50;

        public int getEarnPerAmount() { return earnPerAmount; }
        public void setEarnPerAmount(int earnPerAmount) { this.earnPerAmount = earnPerAmount; }
        public int getPointValue() { return pointValue; }
        public void setPointValue(int pointValue) { this.pointValue = pointValue; }
        public int getMaxRedeemPercent() { return maxRedeemPercent; }
        public void setMaxRedeemPercent(int maxRedeemPercent) { this.maxRedeemPercent = maxRedeemPercent; }
        public int getSignupBonus() { return signupBonus; }
        public void setSignupBonus(int signupBonus) { this.signupBonus = signupBonus; }
    }

    public static class Notification {
        private boolean emailEnabled = false;
        private String from = "no-reply@restaurant.local";
        /** "log" simulates SMS delivery by writing to the application log. */
        private String smsProvider = "log";

        public boolean isEmailEnabled() { return emailEnabled; }
        public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getSmsProvider() { return smsProvider; }
        public void setSmsProvider(String smsProvider) { this.smsProvider = smsProvider; }
    }

    public static class Payment {
        /** Shared secret used to sign/verify gateway responses. */
        private String gatewaySecret = "change-me";

        public String getGatewaySecret() { return gatewaySecret; }
        public void setGatewaySecret(String gatewaySecret) { this.gatewaySecret = gatewaySecret; }
    }
}
