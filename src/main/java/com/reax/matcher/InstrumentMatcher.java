package com.reax.matcher;

import com.reax.datamodel.*;
import org.nustaq.kontraktor.Future;
import org.nustaq.kontraktor.Promise;
import org.nustaq.reallive.ChangeBroadcast;
import org.nustaq.reallive.RLTable;
import org.nustaq.reallive.client.SortedReplicatedSet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Created by ruedi on 19.07.14.
 */
public class InstrumentMatcher { // ready to be be an actor if needed

    RLTable<Order> orders;
    RLTable<Trade> trades;
    Instrument instrument;
    SortedReplicatedSet<Order> buySet;
    SortedReplicatedSet<Order> sellSet;
    Matcher matcher;

    public InstrumentMatcher(Matcher matcher, Instrument record, RLTable<Order> orders, RLTable<Trade> trades) {
        this.matcher = matcher;
        this.instrument = record;
        this.orders = orders;
        this.trades = trades;
        init();
    }

    public void init() {
        checkThread();
        buySet = new SortedReplicatedSet<>((a,b) -> {
            if ( a.getLimitPrice() == b.getLimitPrice() )
                return (int)(a.getCreationTime() - b.getCreationTime());
            return b.getLimitPrice()-a.getLimitPrice();
        });
        sellSet = new SortedReplicatedSet<>((a,b) -> {
            if ( a.getLimitPrice() == b.getLimitPrice() )
                return (int)(a.getCreationTime() - b.getCreationTime());
            return a.getLimitPrice()-b.getLimitPrice();
        });

    }

    // return null or error (FIXME: style)
    public Future<String> addOrder(Order order, User enteringUser) {
        Promise<String> res = new Promise();

        // TODO: calc risk in advance

        if ( order.isBuy() ) {
            // directly add
            buySet.onChangeReceived( ChangeBroadcast.NewAdd("Order", order, 0) );
            orders.$put( order.getRecordKey(), order, Matcher.MATCHER_ID ); // => will be ignored then
            res.signal();

        } else { // sell
            // directly add
            sellSet.onChangeReceived( ChangeBroadcast.NewAdd("Order", order, 0) );
            orders.$put( order.getRecordKey(), order, Matcher.MATCHER_ID ); // => will be ignored then
            res.signal();
        }

        return res;
    }

    public void onARUChange(ChangeBroadcast<Order> change) {
        checkThread();
        // filter out self induced stuff
        if ( change.getOriginator() != Matcher.MATCHER_ID ) {
//            if ( change.getOriginator() != 2 ) {
//                System.out.println("unexpected change source "+change);
//            }
            if ( change.getRecord().isBuy() ) {
                buySet.onChangeReceived(change);
            } else {
                sellSet.onChangeReceived(change);
            }
        }
        if ( buySet.isSnaphotFinished() && sellSet.isSnaphotFinished() ) {
            match();
        }
    }


    int tradesCreated = 0;
    Thread t = null;
    private void checkThread() {
        if ( t == null )
            t = Thread.currentThread();
        else if ( t != Thread.currentThread() )
            throw new RuntimeException("Wrong thread:"+t.getName());
    }

    public void match() {
        checkThread();
        int matchPrc = 0;
        int matchQty = 0;
        if ( instrument.getRecordKey().startsWith("germany")) {
            dumpOB();
        }
        while ( buySet.getSize() > 0 && sellSet.getSize() > 0 ) {
            Order bestBuy;
            Order bestSell;
            try {
                bestBuy = buySet.getTreeSet().first();
                bestSell = sellSet.getTreeSet().first();
            } catch (NoSuchElementException nse) {
                System.out.println("NSE sizes "+buySet.getSize()+" "+sellSet.getSize());
                return;
            }
            if ( bestBuy.getLimitPrice() >= bestSell.getLimitPrice() ) {
                Trade newTrade = trades.createForAdd();

                newTrade.setMarketId(instrument.getMarketPlace());
                newTrade.setInstrumentName(instrument.getName());
                // just use first part of key as name
                String marketPlace = instrument.getMarketPlace();
                if ( marketPlace.indexOf("#") > 0 ) {
                    marketPlace = marketPlace.substring(0,marketPlace.indexOf('#'));
                }

                orders.prepareForUpdate(bestBuy); // mutates record !
                orders.prepareForUpdate(bestSell);

                newTrade.setMarketId(marketPlace);
                newTrade.setInstrumentKey(instrument.getRecordKey());
                newTrade.setBuyTraderKey(bestBuy.getTraderKey());
                newTrade.setSellTraderKey(bestSell.getTraderKey());
                newTrade.setTradeTimeStamp(System.currentTimeMillis());
                newTrade.setBuyOrderId(bestBuy.getRecordKey());
                newTrade.setSellOrderId(bestSell.getRecordKey());

                int trdprice = Math.min(bestBuy.getLimitPrice(), bestSell.getLimitPrice());
                int trdqty = Math.min(bestBuy.getQty(),bestSell.getQty());
                newTrade.setTradePrice(trdprice);
                newTrade.setTradeQty(trdqty);
                matchQty = trdqty;
                matchPrc = trdprice;

                instrument.prepareForUpdate(false);
                instrument.setVolumeTraded(instrument.getVolumeTraded()+matchQty*trdprice);
                instrument.setContractsTraded(instrument.getContractsTraded()+matchQty);
                instrument.$apply(Matcher.MATCHER_ID);

                bestBuy.setQty(bestBuy.getQty()-trdqty);
                if ( bestBuy.getQty() == 0) {
                    orders.$remove(bestBuy.getRecordKey(), Matcher.MATCHER_ID);
                    buySet.unsafeRemove(bestBuy.getRecordKey()); // remove this order (avoid double match by async changes coming in
                } else {
                    bestBuy.$apply(Matcher.MATCHER_ID);
                    // order is modified locally anyway => cache in sync
                }

                bestSell.setQty(bestSell.getQty()-trdqty);
                if ( bestSell.getQty() == 0) {
                    orders.$remove(bestSell.getRecordKey(), Matcher.MATCHER_ID);
                    sellSet.unsafeRemove(bestSell.getRecordKey()); // remove this order (avoid double match by async changes coming in
                } else {
                    bestSell.$apply(Matcher.MATCHER_ID);
                    // order is modified locally anyway => cache in sync
                }
                newTrade.$apply(Matcher.MATCHER_ID);
                int volume = newTrade.getTradeQty() * newTrade.getTradePrice();
                matcher.$processMatch(bestSell, matchQty, matchPrc);
                matcher.$processMatch(bestBuy, matchQty, matchPrc);
                tradesCreated++;
//                if ( tradesCreated > 1000 )
//                    System.out.println("POK");
                // do not loop, will be triggered by bcasts
            } else {
                break; // nothing to match
            }
        }
        updateBstBidAsk(matchPrc,matchQty);
    }

    private void dumpOB() {
        System.out.println("-------------------------------------------------------"+Thread.currentThread().getName());
        System.out.println("Orderbook for "+instrument.getRecordKey());
        List<Order> buys = new ArrayList<>(buySet.getTreeSet());
        List<Order> sells = new ArrayList<>(sellSet.getTreeSet());
        for (int i = 0; i < Math.min(sells.size(),buys.size()); i++) {
            Order s = sells.get(i);
            Order b = buys.get(i);
            System.out.println("BUY/SELL "+b.getQty()+"\t"+b.getLimitPrice()+"\t \t"+s.getLimitPrice()+"\t"+s.getQty());
        }
    }

    private void updateBstBidAsk(int matchPrc, int matchQty) {
        // compute top of book
        int buyQuan = 0;
        int buyPrc = 0;
        for (Iterator<Order> iterator = buySet.getTreeSet().iterator(); iterator.hasNext(); ) {
            Order next = iterator.next();
            if ( buyPrc == 0 )
                buyPrc = next.getLimitPrice();
            if ( buyPrc == next.getLimitPrice() ) {
                buyQuan += next.getQty();
            } else
                break;
        }
        int sellQuan = 0;
        int sellPrc = 0;
        for (Iterator<Order> iterator = sellSet.getTreeSet().iterator(); iterator.hasNext(); ) {
            Order next = iterator.next();
            if ( sellPrc == 0 )
                sellPrc = next.getLimitPrice();
            if ( sellPrc == next.getLimitPrice() ) {
                sellQuan += next.getQty();
            } else
                break;
        }
        instrument.prepareForUpdate(false);
        if ( matchPrc > 0 ) {
            instrument.setLastPrc(matchPrc);
            instrument.setLastQty(matchQty);
            instrument.setLastMatch(System.currentTimeMillis());
        }
        instrument.setAsk(sellPrc);
        instrument.setAskQty(sellQuan);
        instrument.setBid(buyPrc);
        instrument.setBidQty(buyQuan);
        instrument.$apply(Matcher.MATCHER_ID);
    }

    public void snapDone(ChangeBroadcast<Order> change) {
        checkThread();
        buySet.onChangeReceived(change);
        sellSet.onChangeReceived(change);
        if ( buySet.isSnaphotFinished() && sellSet.isSnaphotFinished() ) {
            match();
        }
    }

    public Future<Order> delOrder(Order ord) {
        if ( ord.isBuy() ) {
            for (Iterator<Order> iterator = buySet.getTreeSet().iterator(); iterator.hasNext(); ) {
                Order next = iterator.next();
                if ( next.getRecordKey().equals(ord.getRecordKey()) ) {
                    buySet.unsafeRemove(next.getRecordKey());
                    return new Promise<>(next);
                }
            }
        } else {
            for (Iterator<Order> iterator = sellSet.getTreeSet().iterator(); iterator.hasNext(); ) {
                Order next = iterator.next();
                if ( next.getRecordKey().equals(ord.getRecordKey()) ) {
                    sellSet.unsafeRemove(next.getRecordKey());
                    return new Promise<>(next);
                }
            }
        }
        return new Promise(null);
    }
}
