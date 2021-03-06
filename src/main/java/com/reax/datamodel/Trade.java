package com.reax.datamodel;

import org.nustaq.kontraktor.annotations.GenRemote;
import org.nustaq.reallive.Record;
import org.nustaq.reallive.sys.annotations.*;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by ruedi on 18.07.14.
 */
@GenRemote @KeyLen(Keys.TRADE)
public class Trade extends Record {

    public static SimpleDateFormat df = new SimpleDateFormat("yy/MM/dd HH:mm:ss");

    @Hidden
    String buyTraderKey;
    @Hidden
    String sellTraderKey;

    @Hidden
    String buyOrderId;
    @Hidden
    String sellOrderId;

    @RenderStyle("BS") @DisplayWidth("60px") @DisplayName("B/S")
    transient boolean isBuy;

    @Hidden
    long tradeTimeStamp;

    @DisplayWidth("120px")
    String instrumentKey;

    @RenderStyle("Price") @DisplayWidth("80px") @BGColor("rgba(0,0,0,0.2)") @Align("right")
    int tradePrice;

    @RenderStyle("Qty") @DisplayWidth("60px") @DisplayName("Qty")
    int tradeQty;

    @DisplayWidth("160px") @DisplayName("Time")  @ColOrder(20)
    String tradeTime;

    String marketId;
    String instrumentName;

    public String getBuyOrderId() {
        return buyOrderId;
    }

    public void setBuyOrderId(String buyOrderId) {
        this.buyOrderId = buyOrderId;
    }

    public String getSellOrderId() {
        return sellOrderId;
    }

    public void setSellOrderId(String sellOrderId) {
        this.sellOrderId = sellOrderId;
    }

    public long getTradeTimeStamp() {
        return tradeTimeStamp;
    }

    public void setTradeTimeStamp(long tradeTimeStamp) {
        this.tradeTimeStamp = tradeTimeStamp;
        this.tradeTime = df.format(new Date(tradeTimeStamp));
    }

    public String getTradeTime() {
        return tradeTime;
    }

    public void setTradeTime(String tradeTime) {
        this.tradeTime = tradeTime;
    }

    public int getTradePrice() {
        return tradePrice;
    }

    public void setTradePrice(int tradePrice) {
        this.tradePrice = tradePrice;
    }

    public int getTradeQty() {
        return tradeQty;
    }

    public void setTradeQty(int tradeQty) {
        this.tradeQty = tradeQty;
    }

    public String getBuyTraderKey() {
        return buyTraderKey;
    }

    public void setBuyTraderKey(String buyTraderKey) {
        this.buyTraderKey = buyTraderKey;
    }

    public String getSellTraderKey() {
        return sellTraderKey;
    }

    public void setSellTraderKey(String sellTraderKey) {
        this.sellTraderKey = sellTraderKey;
    }

    public boolean isBuy() {
        return isBuy;
    }

    public void setBuy(boolean isBuy) {
        this.isBuy = isBuy;
    }

    public String getInstrumentKey() {
        return instrumentKey;
    }

    public void setInstrumentKey(String instrumentKey) {
        this.instrumentKey = instrumentKey;
    }

    public String getMarketId() {
        return marketId;
    }

    public void setMarketId(String marketId) {
        this.marketId = marketId;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }
}
